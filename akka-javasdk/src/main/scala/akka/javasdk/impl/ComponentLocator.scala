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
import akka.javasdk.annotations.Component
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
import com.typesafe.config.ConfigException
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
  // user config for components from library jars
  private val LibraryConfigPath = "akka.javasdk.library"
  private val LibraryComponentsKey = "components"
  private val LibraryLogUnlistedKey = "log-unlisted-components"
  // the component classes from library jars to load as if defined in the service
  private[impl] val LibraryComponentsConfigPath = s"$LibraryConfigPath.$LibraryComponentsKey"
  private val LibraryLogUnlistedConfigPath = s"$LibraryConfigPath.$LibraryLogUnlistedKey"
  // set by the parent pom for tests and dev mode
  private val ProjectArtifactIdPath = "akka.javasdk.dev-mode.project-artifact-id"
  // Set by the annotation processor in the descriptor of the deployable service, never in a library descriptor.
  // Must be kept in sync with ComponentAnnotationProcessor.java
  private val DescriptorServiceMarkerPath = "akka.javasdk.service"
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

  private val ComponentTypeBaseClasses: Map[String, Class[_]] =
    Map(
      HttpEndpointKey -> classOf[AnyRef],
      GrpcEndpointKey -> classOf[AnyRef],
      McpEndpointKey -> classOf[AnyRef],
      TimedActionKey -> classOf[TimedAction],
      ConsumerKey -> classOf[Consumer],
      EventSourcedEntityKey -> classOf[EventSourcedEntity[_, _]],
      WorkflowKey -> classOf[Workflow[_]],
      KeyValueEntityKey -> classOf[KeyValueEntity[_]],
      ViewKey -> classOf[AnyRef],
      AgentKey -> classOf[Agent],
      AutonomousAgentKey -> classOf[AutonomousAgent],
      EvaluatorKey -> classOf[Evaluator],
      DurableEvaluatorKey -> classOf[DurableEvaluator[_]])

  /**
   * Checks if a filename matches the component descriptor pattern (akka-javasdk-components_*.conf).
   */
  private[impl] def isComponentDescriptor(filename: String): Boolean = {
    filename.startsWith(ComponentDescriptorPrefix) && filename.endsWith(ComponentDescriptorSuffix)
  }

  /**
   * Finds all META-INF/akka-javasdk-components_*.conf files on the classpath. Each artifact has a descriptor with a
   * unique file name, so descriptors in different classpath entries do not shadow each other.
   *
   * @param classLoader
   *   the class loader to use for resource discovery
   * @return
   *   all found descriptor files with their parsed config
   */
  private[impl] def findAllDescriptorFiles(classLoader: ClassLoader): Seq[Descriptor] = {
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

  private[impl] final case class Descriptor(url: URL, config: Config) {
    def fileName: String = url.getPath.substring(url.getPath.lastIndexOf('/') + 1)
    def isService: Boolean =
      config.hasPath(DescriptorServiceMarkerPath) && config.getBoolean(DescriptorServiceMarkerPath)
    def components(componentTypeKey: String): Seq[String] = {
      val path = s"$DescriptorComponentBasePath.$componentTypeKey"
      if (config.hasPath(path)) config.getStringList(path).asScala.toSeq else Seq.empty
    }
  }

  private[impl] final case class ClassifiedDescriptors(
      service: Seq[Descriptor],
      sdkProvided: Seq[Descriptor],
      library: Seq[Descriptor])

  /**
   * Classifies descriptors into the ones of the service, the ones shipped by SDK modules and the ones from component
   * libraries. The service descriptors are all descriptors with the file name of a descriptor that the annotation
   * processor marked, so a stale descriptor from main or test classes stays with the service. When no descriptor is
   * marked, the service descriptors are the ones with a file name that matches `projectArtifactId`. Fails on service
   * descriptors from more than one artifact. Fails on no service descriptor unless library components are listed in
   * config.
   */
  private[impl] def classifyDescriptors(
      descriptors: Seq[Descriptor],
      projectArtifactId: Option[String],
      libraryComponentsListed: Boolean): ClassifiedDescriptors = {
    val (sdkProvided, others) = descriptors.partition(d => SdkProvidedDescriptorNames.contains(d.fileName))
    val markedNames = others.filter(_.isService).map(_.fileName).toSet
    // the file name only identifies the service when nothing is marked, such as in the tests of a library
    val serviceNames =
      if (markedNames.nonEmpty) markedNames
      else
        projectArtifactId.toSet[String].flatMap { id =>
          others.map(_.fileName).filter(_.endsWith(s"_$id$ComponentDescriptorSuffix"))
        }
    val (service, library) = others.partition(d => serviceNames.contains(d.fileName))
    serviceNames.size match {
      case 1                            => ClassifiedDescriptors(service, sdkProvided, library)
      case 0 if libraryComponentsListed => ClassifiedDescriptors(Seq.empty, sdkProvided, library)
      case 0 if library.isEmpty =>
        throw new IllegalStateException(
          "Found no component descriptor for the service. If you have components, it looks like your project needs " +
          "to be recompiled. Run `mvn clean compile` and try again.")
      case 0 =>
        val libraryTestNote =
          if (projectArtifactId.isDefined)
            " If this is a test of a component library, list the components it uses from other libraries in " +
            s"config [$LibraryComponentsConfigPath]."
          else ""
        throw new IllegalStateException(
          s"Found no component descriptor for the service, only descriptors without the service marker " +
          s"[${library.map(_.url).mkString(", ")}]. If one of them belongs to this service, the build output is from " +
          "an earlier SDK version or the service sets the Maven property `non-akka-service` to `true`. " +
          "Run `mvn clean compile`, or remove the property." + libraryTestNote)
      case _ =>
        throw new IllegalStateException(
          s"Found service component descriptors from more than one artifact [${service.map(_.url).mkString(", ")}]. " +
          "Build a component library with the Maven property `non-akka-service` set to `true`.")
    }
  }

  /**
   * Describes the components in library descriptors that are not listed in `akka.javasdk.library.components`. When
   * nothing is listed, also shows the config to load them. None if there are no such components.
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
      val docsNote =
        "See https://doc.akka.io/sdk/setup-and-dependency-injection.html#_using_components_from_libraries"
      if (listedClassNames.isEmpty)
        s"Found library components that are not loaded. The service loads library components only when they are listed in config [$LibraryComponentsConfigPath].\n" +
        found + "\n" +
        s"To load them, add the components that the service uses to application.conf:\n$LibraryComponentsConfigPath {\n" +
        configLines.mkString("\n") + "\n}\n" + docsNote
      else
        s"Found library components that are not listed in config [$LibraryComponentsConfigPath] and are not loaded:\n" +
        found + "\n" + docsNote
    }
  }

  /**
   * Merges multiple descriptor configs into a single config. Arrays under akka.javasdk.components are concatenated. The
   * service-setup entry is taken from the config that has it. Fails if more than one config has it.
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

  // all components that the SDK adds when the service needs them
  val providedComponents: Seq[Class[_]] = agentProvidedComponents ++ autonomousAgentProvidedComponents

  case class LocatedClasses(components: Seq[Class[_]], service: Option[Class[_]])

  // Descriptor loading uses AnyRef for endpoints and views since the annotation processor already checked them.
  // Config listed classes are user input so they get a stricter check.
  private def libraryComponentTypeCheck(componentTypeKey: String): (Class[_] => Boolean, String) = {
    def annotated(annotation: Class[_ <: java.lang.annotation.Annotation]): (Class[_] => Boolean, String) =
      (_.isAnnotationPresent(annotation), s"a class annotated with @${annotation.getSimpleName}")
    def component(baseClass: Class[_]): (Class[_] => Boolean, String) =
      (
        c => baseClass.isAssignableFrom(c) && c.isAnnotationPresent(classOf[Component]),
        s"a class extending ${baseClass.getName} and annotated with @${classOf[Component].getSimpleName}")
    componentTypeKey match {
      case HttpEndpointKey => annotated(classOf[HttpEndpoint])
      case GrpcEndpointKey => annotated(classOf[GrpcEndpoint])
      case McpEndpointKey  => annotated(classOf[McpEndpoint])
      case ViewKey         => component(classOf[View])
      case other           => component(ComponentTypeBaseClasses(other))
    }
  }

  /**
   * The component type keys and class names listed under `akka.javasdk.library.components` in the application config.
   */
  private[impl] def listedLibraryComponents(applicationConfig: Config): Seq[(String, String)] = {
    if (applicationConfig.hasPath(LibraryConfigPath)) {
      val unknownKeys = applicationConfig
        .getConfig(LibraryConfigPath)
        .root()
        .keySet()
        .asScala
        .filterNot(Set(LibraryComponentsKey, LibraryLogUnlistedKey).contains)
      if (unknownKeys.nonEmpty) {
        throw new IllegalStateException(
          s"Unknown key(s) [${unknownKeys.toSeq.sorted.mkString(", ")}] in config [$LibraryConfigPath]. " +
          s"Supported keys: [$LibraryComponentsKey, $LibraryLogUnlistedKey].")
      }
    }
    if (!applicationConfig.hasPath(LibraryComponentsConfigPath)) Seq.empty
    else {
      val libraryConfig = applicationConfig.getConfig(LibraryComponentsConfigPath)
      val unknownKeys = libraryConfig.root().keySet().asScala.filterNot(ComponentTypeBaseClasses.contains)
      if (unknownKeys.nonEmpty) {
        throw new IllegalStateException(
          s"Unknown component type(s) [${unknownKeys.toSeq.sorted.mkString(", ")}] in config [$LibraryComponentsConfigPath]. " +
          s"Supported component types: [${AllComponentTypeKeys.mkString(", ")}].")
      }

      val listed: Seq[(String, String)] = AllComponentTypeKeys.flatMap { componentTypeKey =>
        if (libraryConfig.hasPath(componentTypeKey)) {
          val classNames =
            try libraryConfig.getStringList(componentTypeKey).asScala
            catch {
              case ex: ConfigException.WrongType =>
                throw new IllegalStateException(
                  s"Config [$LibraryComponentsConfigPath.$componentTypeKey] must be a list of class names.",
                  ex)
            }
          classNames.map(componentTypeKey -> _)
        } else Seq.empty
      }
      val allClassNames = listed.map(_._2)
      val duplicates = allClassNames.diff(allClassNames.distinct).distinct
      if (duplicates.nonEmpty) {
        throw new IllegalStateException(
          s"Duplicate library component(s) [${duplicates.mkString(", ")}] in config [$LibraryComponentsConfigPath]. " +
          "Each component class should only be listed once.")
      }
      val sdkProvided = allClassNames.filter(name => providedComponents.exists(_.getName == name))
      if (sdkProvided.nonEmpty) {
        throw new IllegalStateException(
          s"SDK component(s) [${sdkProvided.mkString(", ")}] listed in config [$LibraryComponentsConfigPath]. " +
          "The SDK loads its own components when needed. Remove them from the config.")
      }
      listed
    }
  }

  /**
   * Loads the listed library component classes, see [[listedLibraryComponents]]. Skips classes already located through
   * descriptor files.
   */
  private[impl] def loadLibraryComponents(
      listed: Seq[(String, String)],
      dynamicAccess: DynamicAccess,
      descriptorComponents: Set[Class[_]]): Seq[Class[_]] = {
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
              s"does not match component type [$componentTypeKey], expected $expectedTypeDescription.")
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

  def locateUserComponents(system: ActorSystem[_]): LocatedClasses =
    locateComponents(system.dynamicAccess, ApplicationConfig(system).getConfig)

  private[impl] def locateComponents(dynamicAccess: DynamicAccess, applicationConfig: Config): LocatedClasses = {
    // Alternative to but inspired by the stdlib SPI style of registering in META-INF/services
    // since we don't always have top supertypes and want to inject things into component constructors
    logger.info(
      "Looking for component descriptors matching pattern [{}{}*{}]",
      MetaInfPath,
      ComponentDescriptorPrefix,
      ComponentDescriptorSuffix: Any)

    // Find all descriptor files on the classpath (service, SDK modules and library JARs)
    val allDescriptors = findAllDescriptorFiles(dynamicAccess.classLoader)
    logger.debug("Found [{}] component descriptor file(s)", allDescriptors.size)

    val listedLibrary = listedLibraryComponents(applicationConfig)
    val listedLibraryClassNames = listedLibrary.map(_._2).toSet
    val projectArtifactId =
      if (applicationConfig.hasPath(ProjectArtifactIdPath))
        Some(applicationConfig.getString(ProjectArtifactIdPath)).filter(_.nonEmpty)
      else None
    val classified =
      classifyDescriptors(allDescriptors, projectArtifactId, libraryComponentsListed = listedLibraryClassNames.nonEmpty)
    classified.library.filter(_.config.hasPath(DescriptorServiceSetupEntryPath)).foreach { descriptor =>
      logger.warn(
        "Ignoring service setup [{}] in library component descriptor [{}]",
        descriptor.config.getString(DescriptorServiceSetupEntryPath),
        descriptor.url)
    }

    val descriptorConfig = mergeDescriptorConfigs((classified.service ++ classified.sdkProvided).map(_.config))
    val componentConfig =
      if (descriptorConfig.hasPath(DescriptorComponentBasePath)) descriptorConfig.getConfig(DescriptorComponentBasePath)
      else ConfigFactory.empty()

    val descriptorComponents: Seq[Class[_]] = ComponentTypeBaseClasses.flatMap {
      case (componentTypeKey, componentTypeClass) =>
        if (componentConfig.hasPath(componentTypeKey)) {
          componentConfig.getStringList(componentTypeKey).asScala.map { className =>
            try {
              val componentClass = dynamicAccess.getClassFor(className)(ClassTag(componentTypeClass)).get
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

    unlistedLibraryComponentsMessage(classified.library, listedLibraryClassNames).foreach { message =>
      val logUnlisted = applicationConfig.getBoolean(LibraryLogUnlistedConfigPath)
      if (!logUnlisted) logger.debug(message)
      else if (listedLibraryClassNames.isEmpty) logger.warn(message)
      else logger.info(message)
    }

    val libraryComponents =
      loadLibraryComponents(listedLibrary, dynamicAccess, descriptorComponents.toSet)
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
      val serviceSetup = dynamicAccess.getClassFor[AnyRef](serviceSetupClassName).get
      logger.debug("Found and loaded service class setup: [{}]", serviceSetup)
      LocatedClasses(withBuildInComponents, Some(serviceSetup))
    } else {
      LocatedClasses(withBuildInComponents, None)
    }
  }
}
