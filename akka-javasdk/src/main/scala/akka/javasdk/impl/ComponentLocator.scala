/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import java.io.File
import java.net.JarURLConnection
import java.net.URI

import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag
import scala.util.Using

import akka.actor.typed.ActorSystem
import akka.annotation.InternalApi
import akka.javasdk.agent.Agent
import akka.javasdk.agent.PromptTemplate
import akka.javasdk.agent.SessionMemoryEntity
import akka.javasdk.agent.evaluator.HallucinationEvaluator
import akka.javasdk.agent.evaluator.SummarizationEvaluator
import akka.javasdk.agent.evaluator.ToxicityEvaluator
import akka.javasdk.consumer.Consumer
import akka.javasdk.eventsourcedentity.EventSourcedEntity
import akka.javasdk.keyvalueentity.KeyValueEntity
import akka.javasdk.timedaction.TimedAction
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

  // Component type keys - these must be kept in sync with ComponentAnnotationProcessor.java
  // in the akka-javasdk-annotation-processor module
  val AgentKey = "agent"
  val ConsumerKey = "consumer"
  val EventSourcedEntityKey = "event-sourced-entity"
  val GrpcEndpointKey = "grpc-endpoint"
  val HttpEndpointKey = "http-endpoint"
  val KeyValueEntityKey = "key-value-entity"
  val McpEndpointKey = "mcp-endpoint"
  val TimedActionKey = "timed-action"
  val ViewKey = "view"
  val WorkflowKey = "workflow"

  private val AllComponentTypeKeys = Seq(
    AgentKey,
    ConsumerKey,
    EventSourcedEntityKey,
    GrpcEndpointKey,
    HttpEndpointKey,
    KeyValueEntityKey,
    McpEndpointKey,
    TimedActionKey,
    ViewKey,
    WorkflowKey)

  private val logger = LoggerFactory.getLogger(getClass)

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
   *   a sequence of parsed Config objects from all found descriptor files
   */
  def findAllDescriptorFiles(classLoader: ClassLoader): Seq[Config] = {
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
          // Inside a JAR file
          val jarConnection = metaInfUrl.openConnection().asInstanceOf[JarURLConnection]
          Using(jarConnection.getJarFile) { jarFile =>
            val entries = jarFile.entries().asScala
            entries
              .filter(e =>
                e.getName.startsWith(MetaInfPath) && isComponentDescriptor(e.getName.stripPrefix(MetaInfPath)))
              .foreach { entry =>
                val url = new URI(s"jar:${jarConnection.getJarFileURL}!/${entry.getName}").toURL
                logger.debug("Found descriptor file in JAR: [{}]", url)
                descriptorUrls += url
              }
          }.recover { case ex =>
            logger.warn("Failed to read JAR file for META-INF scanning: [{}]", ex.getMessage)
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
      config
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

  val providedComponents: Seq[Class[_]] = Seq(
    classOf[SessionMemoryEntity],
    classOf[PromptTemplate],
    classOf[ToxicityEvaluator],
    classOf[SummarizationEvaluator],
    classOf[HallucinationEvaluator])

  case class LocatedClasses(components: Seq[Class[_]], service: Option[Class[_]])

  def locateUserComponents(system: ActorSystem[_]): LocatedClasses = {
    val akkaComponentTypeAndBaseClasses: Map[String, Class[_]] =
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
        ComponentType.Agent -> classOf[Agent])

    // Alternative to but inspired by the stdlib SPI style of registering in META-INF/services
    // since we don't always have top supertypes and want to inject things into component constructors
    logger.info(
      "Looking for component descriptors matching pattern [{}{}*{}]",
      MetaInfPath,
      ComponentDescriptorPrefix,
      ComponentDescriptorSuffix: Any)

    // Find and merge all descriptor files from the classpath (project + library JARs)
    val allDescriptors = findAllDescriptorFiles(system.dynamicAccess.classLoader)
    if (allDescriptors.isEmpty) {
      throw new IllegalStateException(
        "No component descriptor files found. If you have components, it looks like your project needs to be recompiled. Run `mvn clean compile` and try again.")
    }
    logger.debug("Found [{}] component descriptor file(s)", allDescriptors.size)

    val descriptorConfig = mergeDescriptorConfigs(allDescriptors)
    if (!descriptorConfig.hasPath(DescriptorComponentBasePath))
      throw new IllegalStateException(
        "No components found. If you have any, it looks like your project needs to be recompiled. Run `mvn clean compile` and try again.")
    val componentConfig = descriptorConfig.getConfig(DescriptorComponentBasePath)

    val components: Seq[Class[_]] = akkaComponentTypeAndBaseClasses.flatMap {
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

    val withBuildInComponents = if (components.exists(classOf[Agent].isAssignableFrom)) {
      logger.debug("Agent component detected, adding provided components")
      providedComponents ++ components
    } else {
      components
    }

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
