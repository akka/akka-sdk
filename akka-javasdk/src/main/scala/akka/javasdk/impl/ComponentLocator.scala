/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import java.io.File
import java.net.JarURLConnection
import java.net.URI
import java.net.URL

import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag
import scala.util.Failure
import scala.util.Success

import akka.actor.DynamicAccess
import akka.actor.typed.ActorSystem
import akka.annotation.InternalApi
import akka.javasdk.agent.Agent
import akka.javasdk.agent.PromptTemplate
import akka.javasdk.agent.SessionMemoryEntity
import akka.javasdk.agent.autonomous.AutonomousAgent
import akka.javasdk.agent.evaluator.HallucinationEvaluator
import akka.javasdk.agent.evaluator.SummarizationEvaluator
import akka.javasdk.agent.evaluator.ToxicityEvaluator
import akka.javasdk.agent.task.TaskEntity
import akka.javasdk.annotations.GrpcEndpoint
import akka.javasdk.annotations.http.HttpEndpoint
import akka.javasdk.annotations.mcp.McpEndpoint
import akka.javasdk.consumer.Consumer
import akka.javasdk.evaluation.DurableEvaluator
import akka.javasdk.evaluation.Evaluator
import akka.javasdk.eventsourcedentity.EventSourcedEntity
import akka.javasdk.impl.agent.task.BacklogEntity
import akka.javasdk.keyvalueentity.KeyValueEntity
import akka.javasdk.timedaction.TimedAction
import akka.javasdk.view.View
import akka.javasdk.workflow.Workflow
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import org.slf4j.LoggerFactory

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] object ComponentLocator {

  // populated by annotation processor - pattern for artifact-specific descriptors
  private val ComponentDescriptorPrefix = "akka-javasdk-components_"
  private val ComponentDescriptorSuffix = ".conf"
  private val MetaInfPath = "META-INF/"
  val DescriptorComponentBasePath = "akka.javasdk.components"
  val DescriptorServiceSetupEntryPath = "akka.javasdk.service-setup"
  // user config listing components from library jars to load as if defined in the service
  val LibraryComponentsConfigPath = "akka.javasdk.library.components"
  // Set by the annotation processor in the descriptor of the deployable service, never in a library descriptor.
  // Must be kept in sync with ComponentAnnotationProcessor.java
  val DescriptorServiceMarkerPath = "akka.javasdk.service"
  // descriptors shipped by SDK modules, always loaded together with the service descriptor
  private val SdkProvidedDescriptorNames = Set("akka-javasdk-components_akka-javasdk-testkit.conf")

  // Component type keys - these must be kept in sync with ComponentAnnotationProcessor.java
  // in the akka-javasdk-annotation-processor module
  val AgentKey = "agent"
  val AutonomousAgentKey = "autonomous-agent"
  val EvaluatorKey = "evaluator"
  val ConsumerKey = "consumer"
  val EventSourcedEntityKey = "event-sourced-entity"
  val GrpcEndpointKey = "grpc-endpoint"
  val HttpEndpointKey = "http-endpoint"
  val KeyValueEntityKey = "key-value-entity"
  val McpEndpointKey = "mcp-endpoint"
  val TimedActionKey = "timed-action"
  val ViewKey = "view"
  val WorkflowKey = "workflow"
  val DurableEvaluatorKey = "durable-evaluator"

  private val AllComponentTypeKeys = Seq(
    AgentKey,
    AutonomousAgentKey,
    EvaluatorKey,
    ConsumerKey,
    EventSourcedEntityKey,
    GrpcEndpointKey,
    HttpEndpointKey,
    KeyValueEntityKey,
    McpEndpointKey,
    TimedActionKey,
    ViewKey,
    WorkflowKey,
    DurableEvaluatorKey)

  private val logger = LoggerFactory.getLogger(getClass)

  private val akkaComponentTypeAndBaseClasses: Map[String, Class[_]] =
    Map(
      ComponentType.HttpEndpoint -> classOf[AnyRef],
      ComponentType.GrpcEndpoint -> classOf[AnyRef],
      ComponentType.McpEndpoint -> classOf[AnyRef],
      ComponentType.TimedAction -> classOf[TimedAction],
      ComponentType.Consumer -> classOf[Consumer],
      ComponentType.EventSourcedEntity -> classOf[EventSourcedEntity[_, _]],
      ComponentType.Workflow -> classOf[Workflow[_]],
      ComponentType.KeyValueEntity -> classOf[KeyValueEntity[_]],
      ComponentType.View -> classOf[AnyRef],
      ComponentType.Agent -> classOf[Agent],
      ComponentType.AutonomousAgent -> classOf[AutonomousAgent],
      ComponentType.Evaluator -> classOf[Evaluator],
      ComponentType.DurableEvaluator -> classOf[DurableEvaluator[_]])

  /**
   * Checks if a filename matches the component descriptor pattern (akka-javasdk-components_*.conf).
   */
  private[impl] def isComponentDescriptor(filename: String): Boolean = {
    filename.startsWith(ComponentDescriptorPrefix) && filename.endsWith(ComponentDescriptorSuffix)
  }

  /**
   * Finds all META-INF/akka-javasdk-components_*.conf files on the classpath. This allows components to be defined in
   * library JARs with unique filenames per artifact, avoiding shadowing when multiple JARs are on the classpath.
   *
   * Also supports the legacy single-file format (akka-javasdk-components.conf) for backwards compatibility.
   *
   * @param classLoader
   *   the class loader to use for resource discovery
   * @return
   *   all found descriptor files with their parsed config
   */
  def findAllDescriptorFiles(classLoader: ClassLoader): Seq[Descriptor] = {
    val descriptorUrls = scala.collection.mutable.LinkedHashSet.empty[java.net.URL]

    // Find all META-INF directories on the classpath
    val metaInfResources = classLoader.getResources(MetaInfPath).asScala.toSeq

    metaInfResources.foreach { metaInfUrl =>
      logger.debug("Scanning META-INF at [{}]", metaInfUrl)
      metaInfUrl.getProtocol match {
        case "file" =>
          // Directory on filesystem (e.g., target/classes/META-INF)
          val metaInfDir = new File(metaInfUrl.toURI)
          if (metaInfDir.isDirectory) {
            val matchingFiles = metaInfDir.listFiles().filter(f => isComponentDescriptor(f.getName))
            matchingFiles.foreach { file =>
              logger.debug("Found descriptor file: [{}]", file.toURI.toURL)
              descriptorUrls += file.toURI.toURL
            }
          }

        case "jar" =>
          // Inside a JAR file. When useCaches is true (the default), getJarFile() returns the
          // JVM-wide instance owned by JarURLConnection's JarFileFactory, shared by every reader
          // of that jar in the process - it must not be closed here, or every other reader
          // breaks concurrently (e.g. akka.util.ManifestInfo, which re-reads every classpath
          // jar's manifest on each ActorSystem start). With caching off, getJarFile() instead
          // opens a fresh JarFile that only we hold, so closing it is our responsibility - not
          // closing it there would leak a file handle every call.
          val jarConnection = metaInfUrl.openConnection().asInstanceOf[JarURLConnection]
          val jarFile = jarConnection.getJarFile
          try {
            val entries = jarFile.entries().asScala
            entries
              .filter(e =>
                e.getName.startsWith(MetaInfPath) && isComponentDescriptor(e.getName.stripPrefix(MetaInfPath)))
              .foreach { entry =>
                val url = new URI(s"jar:${jarConnection.getJarFileURL}!/${entry.getName}").toURL
                logger.debug("Found descriptor file in JAR: [{}]", url)
                descriptorUrls += url
              }
          } finally {
            if (!jarConnection.getUseCaches) jarFile.close()
          }

        case protocol =>
          logger.debug("Skipping META-INF with unsupported protocol: [{}]", protocol)
      }
    }

    logger.debug("Total descriptor files found: [{}]", descriptorUrls.size)
    descriptorUrls.zipWithIndex.foreach { case (url, idx) =>
      logger.debug("  [{}] [{}]", idx + 1, url)
    }

    descriptorUrls.toSeq.map { url =>
      val config = ConfigFactory.parseURL(url)
      logger.debug("Parsed config from [{}]: [{}]", url, config.root().render())
      Descriptor(url, config)
    }
  }

  final case class Descriptor(url: URL, config: Config) {
    def fileName: String = url.getPath.substring(url.getPath.lastIndexOf('/') + 1)
    def isService: Boolean =
      config.hasPath(DescriptorServiceMarkerPath) && config.getBoolean(DescriptorServiceMarkerPath)
    def components(componentTypeKey: String): Seq[String] = {
      val path = s"$DescriptorComponentBasePath.$componentTypeKey"
      if (config.hasPath(path)) config.getStringList(path).asScala.toSeq else Seq.empty
    }
  }

  final case class SortedDescriptors(
      service: Option[Descriptor],
      sdkProvided: Seq[Descriptor],
      library: Seq[Descriptor])

  /**
   * Sorts descriptors into the one of the deployable service, the ones shipped by SDK modules and the ones from
   * component libraries. Fails on more than one service descriptor. Fails on no service descriptor unless library
   * components are listed in config, for example when testing a component library.
   */
  private[impl] def sortDescriptors(
      descriptors: Seq[Descriptor],
      libraryComponentsListed: Boolean): SortedDescriptors = {
    val (sdkProvided, others) = descriptors.partition(d => SdkProvidedDescriptorNames.contains(d.fileName))
    val (service, library) = others.partition(_.isService)
    service match {
      case Seq(serviceDescriptor)           => SortedDescriptors(Some(serviceDescriptor), sdkProvided, library)
      case Seq() if libraryComponentsListed => SortedDescriptors(None, sdkProvided, library)
      case Seq() =>
        val libraryDescriptors =
          if (library.isEmpty) ""
          else s" Only found library component descriptors [${library.map(_.url).mkString(", ")}]."
        throw new IllegalStateException(
          s"No component descriptor for the service found.$libraryDescriptors " +
          "If you have components, it looks like your project needs to be recompiled. Run `mvn clean compile` and try again.")
      case multiple =>
        throw new IllegalStateException(
          s"More than one service component descriptor found [${multiple.map(_.url).mkString(", ")}]. " +
          "A component library should be a plain Java library that does not use the Akka parent pom.")
    }
  }

  /**
   * Describes the components in library descriptors that are not listed in `akka.javasdk.library.components`, and the
   * config to load them. None if there are no such components.
   */
  private[impl] def unlistedLibraryComponentsMessage(
      libraryDescriptors: Seq[Descriptor],
      listedClassNames: Set[String]): Option[String] = {
    val unlisted: Seq[(Descriptor, Seq[(String, Seq[String])])] = libraryDescriptors.flatMap { descriptor =>
      val byType = AllComponentTypeKeys.flatMap { componentTypeKey =>
        val classNames = descriptor.components(componentTypeKey).filterNot(listedClassNames.contains)
        Option.when(classNames.nonEmpty)(componentTypeKey -> classNames)
      }
      Option.when(byType.nonEmpty)(descriptor -> byType)
    }
    Option.when(unlisted.nonEmpty) {
      val found = unlisted
        .map { case (descriptor, byType) =>
          s"  [${descriptor.url}]\n" + byType
            .map { case (componentTypeKey, classNames) => s"    $componentTypeKey: [${classNames.mkString(", ")}]" }
            .mkString("\n")
        }
        .mkString("\n")
      val configLines = AllComponentTypeKeys.flatMap { componentTypeKey =>
        val classNames = unlisted.flatMap(_._2).collect { case (`componentTypeKey`, names) => names }.flatten
        Option.when(classNames.nonEmpty)(
          s"""  $componentTypeKey = [${classNames.map(n => s"\"$n\"").mkString(", ")}]""")
      }
      s"Found library components that are not loaded. Library components are only loaded when listed in config [$LibraryComponentsConfigPath].\n" +
      found + "\n" +
      s"To load them, add the components to use to application.conf:\n$LibraryComponentsConfigPath {\n" +
      configLines.mkString("\n") + "\n}"
    }
  }

  /**
   * Merges multiple descriptor configs into a single config. Arrays under akka.javasdk.components are concatenated. The
   * service-setup entry is taken from whichever config has it (assumes only one defines it).
   *
   * @param configs
   *   the configs to merge
   * @return
   *   a merged Config containing all components from all descriptors
   */
  def mergeDescriptorConfigs(configs: Seq[Config]): Config = {
    logger.debug("Merging [{}] descriptor config(s)", configs.size)
    if (configs.isEmpty) {
      return ConfigFactory.empty()
    }

    val mergedComponents: Map[String, java.util.List[String]] = AllComponentTypeKeys.flatMap { componentType =>
      val path = s"$DescriptorComponentBasePath.$componentType"
      var seen = Set.empty[String]
      val allValues = configs.flatMap { config =>
        if (config.hasPath(path)) {
          val values = config.getStringList(path).asScala
          val duplicates = values.filter(seen.contains)
          if (duplicates.nonEmpty) {
            throw new IllegalStateException(
              s"Duplicate component(s) found in descriptor files: ${duplicates.mkString(", ")}. " +
              "Each component class should only be declared once.")
          }
          seen = seen ++ values
          values
        } else {
          Seq.empty
        }
      }
      Option.when(allValues.nonEmpty) {
        logger.debug("Found components for [{}]: [{}]", componentType, allValues.mkString(","))
        (componentType -> allValues.asJava)
      }
    }.toMap

    // Find service-setup from any config that has it (must be unique if present)
    val serviceSetups = configs.filter(_.hasPath(DescriptorServiceSetupEntryPath))
    if (serviceSetups.size > 1) {
      val setupClasses = serviceSetups.map(_.getString(DescriptorServiceSetupEntryPath))
      throw new IllegalStateException(
        s"Multiple service-setup classes found in descriptor files: ${setupClasses.mkString(", ")}. " +
        "Only one descriptor file should define a service-setup.")
    }
    val serviceSetup: Option[String] = serviceSetups.headOption.map(_.getString(DescriptorServiceSetupEntryPath))

    val componentsConfig = ConfigFactory.parseMap(mergedComponents.asJava, "merged components")
    val withComponents = ConfigFactory
      .empty()
      .withValue(DescriptorComponentBasePath, componentsConfig.root())

    serviceSetup match {
      case Some(setupClass) =>
        withComponents.withValue(DescriptorServiceSetupEntryPath, ConfigValueFactory.fromAnyRef(setupClass))
      case None =>
        withComponents
    }
  }

  val agentProvidedComponents: Seq[Class[_]] = Seq(
    classOf[SessionMemoryEntity],
    classOf[PromptTemplate],
    classOf[ToxicityEvaluator],
    classOf[SummarizationEvaluator],
    classOf[HallucinationEvaluator])

  val autonomousAgentProvidedComponents: Seq[Class[_]] =
    Seq(classOf[TaskEntity], classOf[BacklogEntity])

  // Keep for backwards compat with anything referencing the old name
  val providedComponents: Seq[Class[_]] = agentProvidedComponents ++ autonomousAgentProvidedComponents

  case class LocatedClasses(components: Seq[Class[_]], service: Option[Class[_]])

  // Descriptor loading uses AnyRef for endpoints and views since the annotation processor already checked them.
  // Config listed classes are user input so they get a stricter check.
  private def libraryComponentTypeCheck(componentTypeKey: String): (Class[_] => Boolean, String) =
    componentTypeKey match {
      case HttpEndpointKey => (_.isAnnotationPresent(classOf[HttpEndpoint]), "a class annotated with @HttpEndpoint")
      case GrpcEndpointKey => (_.isAnnotationPresent(classOf[GrpcEndpoint]), "a class annotated with @GrpcEndpoint")
      case McpEndpointKey  => (_.isAnnotationPresent(classOf[McpEndpoint]), "a class annotated with @McpEndpoint")
      case ViewKey         => (classOf[View].isAssignableFrom(_), s"a class extending ${classOf[View].getName}")
      case other =>
        val baseClass = akkaComponentTypeAndBaseClasses(other)
        (baseClass.isAssignableFrom(_), s"a class extending ${baseClass.getName}")
    }

  /**
   * The component type keys and class names listed under `akka.javasdk.library.components` in the application config.
   */
  private[impl] def listedLibraryComponents(applicationConfig: Config): Seq[(String, String)] = {
    if (!applicationConfig.hasPath(LibraryComponentsConfigPath)) Seq.empty
    else {
      val libraryConfig = applicationConfig.getConfig(LibraryComponentsConfigPath)
      val unknownKeys = libraryConfig.root().keySet().asScala.filterNot(akkaComponentTypeAndBaseClasses.contains)
      if (unknownKeys.nonEmpty) {
        throw new IllegalStateException(
          s"Unknown component type(s) [${unknownKeys.toSeq.sorted.mkString(", ")}] in config [$LibraryComponentsConfigPath]. " +
          s"Supported component types: [${AllComponentTypeKeys.mkString(", ")}]")
      }

      val listed: Seq[(String, String)] = AllComponentTypeKeys.flatMap { componentTypeKey =>
        if (libraryConfig.hasPath(componentTypeKey))
          libraryConfig.getStringList(componentTypeKey).asScala.map(componentTypeKey -> _)
        else Seq.empty
      }
      val allClassNames = listed.map(_._2)
      val duplicates = allClassNames.diff(allClassNames.distinct).distinct
      if (duplicates.nonEmpty) {
        throw new IllegalStateException(
          s"Duplicate library component(s) [${duplicates.mkString(", ")}] in config [$LibraryComponentsConfigPath]. " +
          "Each component class should only be listed once.")
      }
      listed
    }
  }

  /**
   * Loads the component classes listed under `akka.javasdk.library.components` in the application config. Classes
   * already located through descriptor files are skipped.
   */
  private[impl] def loadLibraryComponents(
      applicationConfig: Config,
      dynamicAccess: DynamicAccess,
      descriptorComponents: Set[Class[_]]): Seq[Class[_]] = {
    val listed = listedLibraryComponents(applicationConfig)
    AllComponentTypeKeys.flatMap { componentTypeKey =>
      val classNames = listed.collect { case (`componentTypeKey`, className) => className }
      if (classNames.isEmpty) Seq.empty
      else {
        val (isExpectedType, expectedTypeDescription) = libraryComponentTypeCheck(componentTypeKey)
        val loaded = classNames.flatMap { className =>
          val componentClass = dynamicAccess.getClassFor[AnyRef](className) match {
            case Success(cls) => cls
            case Failure(ex) =>
              throw new IllegalStateException(
                s"Could not load library component class [$className] listed in config [$LibraryComponentsConfigPath.$componentTypeKey]. " +
                "Make sure the library containing it is on the classpath.",
                ex)
          }
          if (!isExpectedType(componentClass)) {
            throw new IllegalStateException(
              s"Library component class [$className] listed in config [$LibraryComponentsConfigPath.$componentTypeKey] " +
              s"is not a $componentTypeKey component, expected $expectedTypeDescription")
          }
          if (descriptorComponents.contains(componentClass)) {
            logger.info(
              "Library component [{}] listed in config [{}.{}] is already registered through a component descriptor, ignoring",
              className,
              LibraryComponentsConfigPath,
              componentTypeKey)
            None
          } else Some(componentClass)
        }
        if (loaded.nonEmpty)
          logger.info(
            "Adding library components from config [{}.{}]: [{}]",
            LibraryComponentsConfigPath,
            componentTypeKey,
            loaded.map(_.getName).mkString(", "))
        loaded
      }
    }
  }

  def locateUserComponents(system: ActorSystem[_]): LocatedClasses = {
    // Alternative to but inspired by the stdlib SPI style of registering in META-INF/services
    // since we don't always have top supertypes and want to inject things into component constructors
    logger.info(
      "Looking for component descriptors matching pattern [{}{}*{}]",
      MetaInfPath,
      ComponentDescriptorPrefix,
      ComponentDescriptorSuffix: Any)

    // Find all descriptor files on the classpath (service, SDK modules and library JARs)
    val allDescriptors = findAllDescriptorFiles(system.dynamicAccess.classLoader)
    logger.debug("Found [{}] component descriptor file(s)", allDescriptors.size)

    val applicationConfig = ApplicationConfig(system).getConfig
    val listedLibraryClassNames = listedLibraryComponents(applicationConfig).map(_._2).toSet
    val sorted = sortDescriptors(allDescriptors, libraryComponentsListed = listedLibraryClassNames.nonEmpty)
    sorted.library.filter(_.config.hasPath(DescriptorServiceSetupEntryPath)).foreach { descriptor =>
      logger.warn(
        "Ignoring service setup [{}] in library component descriptor [{}]",
        descriptor.config.getString(DescriptorServiceSetupEntryPath),
        descriptor.url)
    }

    val descriptorConfig = mergeDescriptorConfigs((sorted.service.toSeq ++ sorted.sdkProvided).map(_.config))
    val componentConfig =
      if (descriptorConfig.hasPath(DescriptorComponentBasePath)) descriptorConfig.getConfig(DescriptorComponentBasePath)
      else ConfigFactory.empty()

    val descriptorComponents: Seq[Class[_]] = akkaComponentTypeAndBaseClasses.flatMap {
      case (componentTypeKey, componentTypeClass) =>
        if (componentConfig.hasPath(componentTypeKey)) {
          componentConfig.getStringList(componentTypeKey).asScala.map { className =>
            try {
              val componentClass = system.dynamicAccess.getClassFor(className)(ClassTag(componentTypeClass)).get
              logger.debug("Found and loaded component class: [{}]", componentClass)
              componentClass
            } catch {
              case ex: ClassNotFoundException =>
                throw new IllegalStateException(
                  s"Could not load component class [$className]. The exception might appear after rename or repackaging operation. " +
                  "It looks like your project needs to be recompiled. Run `mvn clean compile` and try again.",
                  ex)
            }
          }
        } else
          Seq.empty
    }.toSeq

    unlistedLibraryComponentsMessage(sorted.library, listedLibraryClassNames).foreach { message =>
      if (listedLibraryClassNames.isEmpty) logger.warn(message)
      else logger.debug(message)
    }

    val libraryComponents =
      loadLibraryComponents(applicationConfig, system.dynamicAccess, descriptorComponents.toSet)
    val components = descriptorComponents ++ libraryComponents
    if (components.isEmpty)
      throw new IllegalStateException(
        "No components found. If you have any, it looks like your project needs to be recompiled. Run `mvn clean compile` and try again.")

    val hasAgentComponents = components.exists(c => classOf[Agent].isAssignableFrom(c))
    val hasAutonomousAgentComponents = components.exists(c => classOf[AutonomousAgent].isAssignableFrom(c))

    if (hasAgentComponents) logger.debug("Agent component detected, adding agent provided components")
    if (hasAutonomousAgentComponents)
      logger.debug("AutonomousAgent component detected, adding autonomous agent provided components")

    val withBuildInComponents =
      (if (hasAgentComponents || hasAutonomousAgentComponents) agentProvidedComponents else Seq.empty) ++
      (if (hasAutonomousAgentComponents) autonomousAgentProvidedComponents else Seq.empty) ++
      components

    if (descriptorConfig.hasPath(DescriptorServiceSetupEntryPath)) {
      // central config/lifecycle class
      val serviceSetupClassName = descriptorConfig.getString(DescriptorServiceSetupEntryPath)
      val serviceSetup = system.dynamicAccess.getClassFor[AnyRef](serviceSetupClassName).get
      logger.debug("Found and loaded service class setup: [{}]", serviceSetup)
      LocatedClasses(withBuildInComponents, Some(serviceSetup))
    } else {
      LocatedClasses(withBuildInComponents, None)
    }
  }
}
