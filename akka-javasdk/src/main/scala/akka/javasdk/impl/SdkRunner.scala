/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import java.lang.reflect.Constructor
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util
import java.util.Optional
import java.util.concurrent.CompletionStage

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.jdk.CollectionConverters._
import scala.jdk.FutureConverters._
import scala.jdk.OptionConverters.RichOption
import scala.jdk.OptionConverters.RichOptional
import scala.reflect.ClassTag
import scala.util.control.NonFatal

import akka.Done
import akka.actor.typed.ActorSystem
import akka.annotation.InternalApi
import akka.grpc.internal.JavaMetadataImpl
import akka.grpc.javadsl.Metadata
import akka.http.javadsl.model.HttpHeader
import akka.http.scaladsl.model.headers.RawHeader
import akka.javasdk.BuildInfo
import akka.javasdk.DependencyProvider
import akka.javasdk.JwtClaims
import akka.javasdk.Principals
import akka.javasdk.Retries
import akka.javasdk.ServiceSetup
import akka.javasdk.Tracing
import akka.javasdk.annotations.ComponentId
import akka.javasdk.annotations.GrpcEndpoint
import akka.javasdk.annotations.Setup
import akka.javasdk.annotations.http.HttpEndpoint
import akka.javasdk.client.ComponentClient
import akka.javasdk.consumer.Consumer
import akka.javasdk.eventsourcedentity.EventSourcedEntity
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext
import akka.javasdk.grpc.AbstractGrpcEndpoint
import akka.javasdk.grpc.GrpcClientProvider
import akka.javasdk.grpc.GrpcRequestContext
import akka.javasdk.http.AbstractHttpEndpoint
import akka.javasdk.http.HttpClientProvider
import akka.javasdk.http.QueryParams
import akka.javasdk.http.RequestContext
import akka.javasdk.impl.ComponentDescriptorFactory.consumerDestination
import akka.javasdk.impl.ComponentDescriptorFactory.consumerSource
import akka.javasdk.impl.Sdk.StartupContext
import akka.javasdk.impl.Validations.Invalid
import akka.javasdk.impl.Validations.Valid
import akka.javasdk.impl.Validations.Validation
import akka.javasdk.impl.client.ComponentClientImpl
import akka.javasdk.impl.consumer.ConsumerImpl
import akka.javasdk.impl.eventsourcedentity.EventSourcedEntityImpl
import akka.javasdk.impl.grpc.GrpcClientProviderImpl
import akka.javasdk.impl.http.HttpClientProviderImpl
import akka.javasdk.impl.http.JwtClaimsImpl
import akka.javasdk.impl.http.QueryParamsImpl
import akka.javasdk.impl.keyvalueentity.KeyValueEntityImpl
import akka.javasdk.impl.reflection.Reflect
import akka.javasdk.impl.reflection.Reflect.Syntax.AnnotatedElementOps
import akka.javasdk.impl.serialization.JsonSerializer
import akka.javasdk.impl.telemetry.SpanTracingImpl
import akka.javasdk.impl.telemetry.TraceInstrumentation
import akka.javasdk.impl.timedaction.TimedActionImpl
import akka.javasdk.impl.timer.TimerSchedulerImpl
import akka.javasdk.impl.view.ViewDescriptorFactory
import akka.javasdk.impl.workflow.WorkflowImpl
import akka.javasdk.keyvalueentity.KeyValueEntity
import akka.javasdk.keyvalueentity.KeyValueEntityContext
import akka.javasdk.timedaction.TimedAction
import akka.javasdk.timer.TimerScheduler
import akka.javasdk.view.View
import akka.javasdk.workflow.Workflow
import akka.javasdk.workflow.Workflow.RunnableStep
import akka.javasdk.workflow.WorkflowContext
import akka.runtime.sdk.spi
import akka.runtime.sdk.spi.ComponentClients
import akka.runtime.sdk.spi.ConsumerDescriptor
import akka.runtime.sdk.spi.EventSourcedEntityDescriptor
import akka.runtime.sdk.spi.GrpcEndpointRequestConstructionContext
import akka.runtime.sdk.spi.HttpEndpointConstructionContext
import akka.runtime.sdk.spi.RegionInfo
import akka.runtime.sdk.spi.RemoteIdentification
import akka.runtime.sdk.spi.SpiComponents
import akka.runtime.sdk.spi.SpiDevModeSettings
import akka.runtime.sdk.spi.SpiEventSourcedEntity
import akka.runtime.sdk.spi.SpiEventingSupportSettings
import akka.runtime.sdk.spi.SpiMockedEventingSettings
import akka.runtime.sdk.spi.SpiServiceInfo
import akka.runtime.sdk.spi.SpiSettings
import akka.runtime.sdk.spi.SpiWorkflow
import akka.runtime.sdk.spi.StartContext
import akka.runtime.sdk.spi.TimedActionDescriptor
import akka.runtime.sdk.spi.UserFunctionError
import akka.runtime.sdk.spi.ViewDescriptor
import akka.runtime.sdk.spi.WorkflowDescriptor
import akka.stream.Materializer
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.{ Context => OtelContext }
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.util.concurrent.Executor

import akka.javasdk.agent.Agent
import akka.javasdk.agent.AgentContext
import akka.javasdk.agent.AgentRegistry
import akka.javasdk.impl.agent.AgentImpl
import akka.runtime.sdk.spi.AgentDescriptor
import akka.runtime.sdk.spi.SpiAgent
import akka.javasdk.agent.PromptTemplate
import akka.javasdk.agent.SessionMemoryEntity
import akka.javasdk.annotations.AgentDescription
import akka.javasdk.impl.agent.AgentRegistryImpl
import akka.javasdk.impl.agent.PromptTemplateClient
import akka.javasdk.annotations.mcp.McpEndpoint
import akka.javasdk.mcp.AbstractMcpEndpoint
import akka.javasdk.mcp.McpRequestContext
import akka.runtime.sdk.spi.McpEndpointConstructionContext

/**
 * INTERNAL API
 */
@InternalApi
object SdkRunner {
  val userServiceLog: Logger = LoggerFactory.getLogger("akka.javasdk.ServiceLog")

  val FutureDone: Future[Done] = Future.successful(Done)
}

/**
 * INTERNAL API
 */
@InternalApi
class SdkRunner private (dependencyProvider: Option[DependencyProvider], disabledComponents: Set[Class[_]])
    extends akka.runtime.sdk.spi.Runner {
  private val startedPromise = Promise[StartupContext]()

  // default constructor for runtime creation
  def this() = this(None, Set.empty[Class[_]])

  // constructor for testkit
  def this(dependencyProvider: java.util.Optional[DependencyProvider], disabledComponents: java.util.Set[Class[_]]) =
    this(dependencyProvider.toScala, disabledComponents.asScala.toSet)

  def applicationConfig: Config =
    ApplicationConfig.loadApplicationConf

  // FIXME: remove nowarn with https://github.com/akka/akka-sdk/pull/401
  @scala.annotation.nowarn("msg=deprecated")
  override def getSettings: SpiSettings = {
    val applicationConf = applicationConfig

    val eventSourcedEntitySnapshotEvery = applicationConfig.getInt("akka.javasdk.event-sourced-entity.snapshot-every")
    val cleanupDeletedEntityAfter =
      applicationConf.getDuration("akka.javasdk.entity.cleanup-deleted-after")

    val cleanupInterval =
      applicationConf.getDuration("akka.javasdk.delete-entity.cleanup-interval")

    val devModeSettings =
      if (applicationConf.getBoolean("akka.javasdk.dev-mode.enabled"))
        Some(
          new SpiDevModeSettings(
            httpPort = applicationConf.getInt("akka.javasdk.dev-mode.http-port"),
            aclEnabled = applicationConf.getBoolean("akka.javasdk.dev-mode.acl.enabled"),
            persistenceEnabled = applicationConf.getBoolean("akka.javasdk.dev-mode.persistence.enabled"),
            serviceName = applicationConf.getString("akka.javasdk.dev-mode.service-name"),
            eventingSupport = extractBrokerConfig(applicationConf.getConfig("akka.javasdk.dev-mode.eventing")),
            mockedEventing = SpiMockedEventingSettings.empty,
            testMode = false))
      else
        None

    new SpiSettings(eventSourcedEntitySnapshotEvery, cleanupDeletedEntityAfter, cleanupInterval, devModeSettings)
  }

  private def extractBrokerConfig(eventingConf: Config): SpiEventingSupportSettings = {
    val brokerConfigName = eventingConf.getString("support")
    SpiEventingSupportSettings.fromConfigValue(
      brokerConfigName,
      if (eventingConf.hasPath(brokerConfigName))
        eventingConf.getConfig(brokerConfigName)
      else
        ConfigFactory.empty())
  }

  override def start(startContext: StartContext): Future[SpiComponents] = {
    try {
      ApplicationConfig(startContext.system).overrideConfig(applicationConfig)
      val app = new Sdk(
        startContext.system,
        startContext.executionContext,
        startContext.materializer,
        startContext.componentClients,
        startContext.remoteIdentification,
        startContext.tracerFactory,
        startContext.regionInfo,
        dependencyProvider,
        disabledComponents,
        startedPromise,
        getSettings.devMode.map(_.serviceName))
      Future.successful(app.spiComponents)
    } catch {
      case NonFatal(ex) =>
        LoggerFactory.getLogger(getClass).error("Unexpected exception while setting up service", ex)
        startedPromise.tryFailure(ex)
        throw ex
    }
  }

  def started: CompletionStage[StartupContext] =
    startedPromise.future.asJava

}

/**
 * INTERNAL API
 */
@InternalApi
private object ComponentType {
  // Those are also defined in ComponentAnnotationProcessor, and must be the same

  val EventSourcedEntity = "event-sourced-entity"
  val KeyValueEntity = "key-value-entity"
  val Workflow = "workflow"
  val HttpEndpoint = "http-endpoint"
  val GrpcEndpoint = "grpc-endpoint"
  val McpEndpoint = "mcp-endpoint"
  val Consumer = "consumer"
  val TimedAction = "timed-action"
  val View = "view"
  val Agent = "agent"
}

/**
 * INTERNAL API
 */
@InternalApi
private object ComponentLocator {

  // populated by annotation processor
  private val ComponentDescriptorResourcePath = "META-INF/akka-javasdk-components.conf"
  private val DescriptorComponentBasePath = "akka.javasdk.components"
  private val DescriptorServiceSetupEntryPath = "akka.javasdk.service-setup"

  private val logger = LoggerFactory.getLogger(getClass)

  case class LocatedClasses(components: Seq[Class[_]], service: Option[Class[_]])

  def locateUserComponents(system: ActorSystem[_]): LocatedClasses = {
    val kalixComponentTypeAndBaseClasses: Map[String, Class[_]] =
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
    logger.info("Looking for component descriptors in [{}]", ComponentDescriptorResourcePath)

    // Descriptor hocon has one entry per component type with a list of strings containing
    // the concrete component classes for the given project
    val descriptorConfig = ConfigFactory.load(ComponentDescriptorResourcePath)
    if (!descriptorConfig.hasPath(DescriptorComponentBasePath))
      throw new IllegalStateException(
        "It looks like your project needs to be recompiled. Run `mvn clean compile` and try again.")
    val componentConfig = descriptorConfig.getConfig(DescriptorComponentBasePath)

    val components: Seq[Class[_]] = kalixComponentTypeAndBaseClasses.flatMap {
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
      logger.debug("Agent component detected, adding built-in components")
      classOf[SessionMemoryEntity] +: classOf[PromptTemplate] +: components
    } else {
      components
    }

    if (descriptorConfig.hasPath(DescriptorServiceSetupEntryPath)) {
      // central config/lifecycle class
      val serviceSetupClassName = descriptorConfig.getString(DescriptorServiceSetupEntryPath)
      val serviceSetup = system.dynamicAccess.getClassFor[AnyRef](serviceSetupClassName).get
      if (serviceSetup.hasAnnotation[Setup]) {
        logger.debug("Found and loaded service class setup: [{}]", serviceSetup)
      } else {
        logger.warn("Ignoring service class [{}] as it does not have the the @Setup annotation", serviceSetup)
      }
      LocatedClasses(withBuildInComponents, Some(serviceSetup))
    } else {
      LocatedClasses(withBuildInComponents, None)
    }
  }
}

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] object Sdk {
  final case class StartupContext(
      componentClients: ComponentClients,
      dependencyProvider: Option[DependencyProvider],
      httpClientProvider: HttpClientProvider,
      grpcClientProvider: GrpcClientProviderImpl,
      agentRegistry: AgentRegistryImpl,
      serializer: JsonSerializer)

  private val platformManagedDependency = Set[Class[_]](
    classOf[ComponentClient],
    classOf[TimerScheduler],
    classOf[HttpClientProvider],
    classOf[GrpcClientProvider],
    classOf[Tracer],
    classOf[Span],
    classOf[Config],
    classOf[WorkflowContext],
    classOf[EventSourcedEntityContext],
    classOf[KeyValueEntityContext],
    classOf[Retries],
    classOf[AgentContext],
    classOf[AgentRegistry])
}

/**
 * INTERNAL API
 */
@InternalApi
private final class Sdk(
    system: ActorSystem[_],
    sdkExecutionContext: ExecutionContext,
    sdkMaterializer: Materializer,
    runtimeComponentClients: ComponentClients,
    remoteIdentification: Option[RemoteIdentification],
    tracerFactory: String => Tracer,
    regionInfo: RegionInfo,
    dependencyProviderOverride: Option[DependencyProvider],
    disabledComponents: Set[Class[_]],
    startedPromise: Promise[StartupContext],
    serviceNameOverride: Option[String]) {
  import Sdk._

  private val logger = LoggerFactory.getLogger(getClass)
  private val serializer = new JsonSerializer
  private lazy val retries = new RetriesImpl(system.classicSystem)
  private val ComponentLocator.LocatedClasses(componentClasses, maybeServiceClass) =
    ComponentLocator.locateUserComponents(system)
  @volatile private var dependencyProviderOpt: Option[DependencyProvider] = dependencyProviderOverride

  private val applicationConfig = ApplicationConfig(system).getConfig
  private val sdkSettings = Settings(applicationConfig.getConfig("akka.javasdk"))

  private val sdkTracerFactory = () => tracerFactory(TraceInstrumentation.InstrumentationScopeName)

  private lazy val httpClientProvider = new HttpClientProviderImpl(
    system,
    None,
    remoteIdentification.map(ri => RawHeader(ri.headerName, ri.headerValue)),
    sdkSettings)

  private lazy val userServiceConfig = {
    // hiding these paths from the config provided to user
    val sensitivePaths = List("akka", "kalix.meta", "kalix.proxy", "kalix.runtime", "system")
    val sdkConfig = applicationConfig.getObject("akka.javasdk")
    sensitivePaths
      .foldLeft(applicationConfig) { (conf, toHide) => conf.withoutPath(toHide) }
      .withValue("akka.javasdk", sdkConfig)
  }

  private lazy val grpcClientProvider = new GrpcClientProviderImpl(
    system,
    sdkSettings,
    userServiceConfig,
    remoteIdentification.map(ri => GrpcClientProviderImpl.AuthHeaders(ri.headerName, ri.headerValue)))

  // validate service classes before instantiating
  private val validation = componentClasses.foldLeft(Valid: Validation) { case (validations, cls) =>
    validations ++ Validations.validate(cls)
  }
  validation match { // if any invalid component, log and throw
    case Valid => ()
    case invalid: Invalid =>
      invalid.messages.foreach { msg => logger.error(msg) }
      invalid.throwFailureSummary()
  }

  private def hasComponentId(clz: Class[_]): Boolean = {
    if (clz.hasAnnotation[ComponentId]) {
      true
    } else {
      //additional check to skip logging for endpoints
      if (!clz.hasAnnotation[HttpEndpoint] && !clz.hasAnnotation[GrpcEndpoint] && !clz.hasAnnotation[McpEndpoint]) {
        //this could happen when we remove the @ComponentId annotation from the class,
        //the file descriptor generated by annotation processor might still have this class entry,
        //for instance when working with IDE and incremental compilation (without clean)
        logger.warn("Ignoring component [{}] as it does not have the @ComponentId annotation", clz.getName)
      }
      false
    }
  }

  // command handlers candidate must have 0 or 1 parameter and return the components effect type
  // we might later revisit this, instead of single param, we can require (State, Cmd) => Effect like in Akka
  def isCommandHandlerCandidate[E](method: Method)(implicit effectType: ClassTag[E]): Boolean = {
    effectType.runtimeClass.isAssignableFrom(method.getReturnType) &&
    method.getParameterTypes.length <= 1 &&
    // Workflow will have lambdas returning Effect, we want to filter them out
    !method.getName.startsWith("lambda$")
  }

  // we need a method instead of function in order to have type params
  // to late use in Reflect.workflowStateType
  private def workflowInstanceFactory[S, W <: Workflow[S]](
      factoryContext: SpiWorkflow.FactoryContext,
      clz: Class[W]): SpiWorkflow = {
    logger.debug(s"Registering Workflow [${clz.getName}]")
    new WorkflowImpl[S, W](
      factoryContext.workflowId,
      clz,
      serializer,
      ComponentDescriptor.descriptorFor(clz, serializer),
      timerClient = runtimeComponentClients.timerClient,
      sdkExecutionContext,
      sdkTracerFactory,
      regionInfo,
      { context =>

        val workflow = wiredInstance(clz) {
          sideEffectingComponentInjects(None).orElse {
            // remember to update component type API doc and docs if changing the set of injectables
            case p if p == classOf[WorkflowContext] => context
          }
        }

        // FIXME pull this inline setup stuff out of SdkRunner and into some workflow class
        val workflowStateType: Class[_] = Reflect.workflowStateType[S, W](workflow)
        serializer.registerTypeHints(workflowStateType)

        workflow
          .definition()
          .getSteps
          .asScala
          .flatMap {
            case asyncCallStep: Workflow.AsyncCallStep[_, _, _] =>
              if (asyncCallStep.transitionInputClass == null) List(asyncCallStep.callInputClass)
              else List(asyncCallStep.callInputClass, asyncCallStep.transitionInputClass)
            case callStep: Workflow.CallStep[_, _, _] =>
              if (callStep.transitionInputClass == null) List(callStep.callInputClass)
              else List(callStep.callInputClass, callStep.transitionInputClass)
            case runnable: RunnableStep => List.empty
          }
          .foreach(serializer.registerTypeHints)

        workflow
      })
  }

  // collect all Endpoints and compose them to build a larger router
  private val httpEndpointDescriptors = componentClasses
    .filter(Reflect.isRestEndpoint)
    .map { httpEndpointClass =>
      HttpEndpointDescriptorFactory(httpEndpointClass, httpEndpointFactory(httpEndpointClass))
    }

  private val grpcEndpointDescriptors = componentClasses
    .filter(Reflect.isGrpcEndpoint)
    .map { grpcEndpointClass =>
      val anyRefClass = grpcEndpointClass.asInstanceOf[Class[AnyRef]]
      GrpcEndpointDescriptorFactory(anyRefClass, grpcEndpointFactory(anyRefClass))(system)
    }

  private val mcpEndpoints = componentClasses
    .filter(Reflect.isMcpEndpoint)
    .map { mcpEndpointClass =>
      val anyRefClass = mcpEndpointClass.asInstanceOf[Class[AnyRef]]
      McpEndpointDescriptorFactory(anyRefClass, mcpEndpointFactory(anyRefClass))(sdkExecutionContext)
    }

  private var eventSourcedEntityDescriptors = Vector.empty[EventSourcedEntityDescriptor]
  private var keyValueEntityDescriptors = Vector.empty[EventSourcedEntityDescriptor]
  private var workflowDescriptors = Vector.empty[WorkflowDescriptor]
  private var timedActionDescriptors = Vector.empty[TimedActionDescriptor]
  private var consumerDescriptors = Vector.empty[ConsumerDescriptor]
  private var viewDescriptors = Vector.empty[ViewDescriptor]
  private var agentDescriptors = Vector.empty[AgentDescriptor]
  private var agentRegistryInfo = Vector.empty[AgentRegistryImpl.AgentDetails]

  componentClasses
    .filter(hasComponentId)
    .foreach {
      case clz if classOf[EventSourcedEntity[_, _]].isAssignableFrom(clz) =>
        val componentId = clz.getAnnotation(classOf[ComponentId]).value

        val readOnlyCommandNames =
          clz.getDeclaredMethods.collect {
            case method
                if isCommandHandlerCandidate[EventSourcedEntity.Effect[_]](method) && method.getReturnType == classOf[
                  EventSourcedEntity.ReadOnlyEffect[_]] =>
              method.getName
          }.toSet

        // we preemptively register the events type to the serializer
        Reflect.allKnownEventSourcedEntityEventType(clz).foreach(serializer.registerTypeHints)

        val entityStateType: Class[AnyRef] = Reflect.eventSourcedEntityStateType(clz).asInstanceOf[Class[AnyRef]]

        val instanceFactory: SpiEventSourcedEntity.FactoryContext => SpiEventSourcedEntity = { factoryContext =>
          new EventSourcedEntityImpl[AnyRef, AnyRef, EventSourcedEntity[AnyRef, AnyRef]](
            sdkTracerFactory,
            componentId,
            factoryContext.entityId,
            serializer,
            ComponentDescriptor.descriptorFor(clz, serializer),
            entityStateType,
            regionInfo,
            context =>
              wiredInstance(clz.asInstanceOf[Class[EventSourcedEntity[AnyRef, AnyRef]]]) {
                // remember to update component type API doc and docs if changing the set of injectables
                case p if p == classOf[EventSourcedEntityContext] => context
              })
        }
        eventSourcedEntityDescriptors :+=
          new EventSourcedEntityDescriptor(
            componentId,
            clz.getName,
            readOnlyCommandNames,
            instanceFactory,
            keyValue = false)

      case clz if classOf[KeyValueEntity[_]].isAssignableFrom(clz) =>
        val componentId = clz.getAnnotation(classOf[ComponentId]).value

        val readOnlyCommandNames =
          clz.getDeclaredMethods.collect {
            case method
                if isCommandHandlerCandidate[KeyValueEntity.Effect[_]](method) && method.getReturnType == classOf[
                  KeyValueEntity.ReadOnlyEffect[_]] =>
              method.getName
          }.toSet

        val entityStateType: Class[AnyRef] = Reflect.keyValueEntityStateType(clz).asInstanceOf[Class[AnyRef]]

        val instanceFactory: SpiEventSourcedEntity.FactoryContext => SpiEventSourcedEntity = { factoryContext =>
          new KeyValueEntityImpl[AnyRef, KeyValueEntity[AnyRef]](
            sdkSettings,
            sdkTracerFactory,
            componentId,
            factoryContext.entityId,
            serializer,
            ComponentDescriptor.descriptorFor(clz, serializer),
            entityStateType,
            regionInfo,
            context =>
              wiredInstance(clz.asInstanceOf[Class[KeyValueEntity[AnyRef]]]) {
                // remember to update component type API doc and docs if changing the set of injectables
                case p if p == classOf[KeyValueEntityContext] => context
              })
        }
        keyValueEntityDescriptors :+=
          new EventSourcedEntityDescriptor(
            componentId,
            clz.getName,
            readOnlyCommandNames,
            instanceFactory,
            keyValue = true)

      case clz if Reflect.isWorkflow(clz) =>
        val componentId = clz.getAnnotation(classOf[ComponentId]).value

        val readOnlyCommandNames =
          clz.getDeclaredMethods.collect {
            case method
                if isCommandHandlerCandidate[Workflow.Effect[_]](method) && method.getReturnType == classOf[
                  Workflow.ReadOnlyEffect[_]] =>
              method.getName
          }.toSet

        workflowDescriptors :+=
          new WorkflowDescriptor(
            componentId,
            clz.getName,
            readOnlyCommandNames,
            ctx => workflowInstanceFactory(ctx, clz.asInstanceOf[Class[Workflow[Nothing]]]))

      case clz if classOf[TimedAction].isAssignableFrom(clz) =>
        val componentId = clz.getAnnotation(classOf[ComponentId]).value
        val timedActionClass = clz.asInstanceOf[Class[TimedAction]]
        val timedActionSpi =
          new TimedActionImpl[TimedAction](
            componentId,
            () => wiredInstance(timedActionClass)(sideEffectingComponentInjects(None)),
            timedActionClass,
            system.classicSystem,
            runtimeComponentClients.timerClient,
            sdkExecutionContext,
            sdkTracerFactory,
            serializer,
            regionInfo,
            ComponentDescriptor.descriptorFor(timedActionClass, serializer))
        timedActionDescriptors :+=
          new TimedActionDescriptor(componentId, clz.getName, timedActionSpi)

      case clz if classOf[Consumer].isAssignableFrom(clz) =>
        val componentId = clz.getAnnotation(classOf[ComponentId]).value
        val consumerClass = clz.asInstanceOf[Class[Consumer]]
        val consumerDest = consumerDestination(consumerClass)
        val consumerSrc = consumerSource(consumerClass)
        val consumerSpi =
          new ConsumerImpl[Consumer](
            componentId,
            () => wiredInstance(consumerClass)(sideEffectingComponentInjects(None)),
            consumerClass,
            consumerSrc,
            consumerDest,
            system.classicSystem,
            runtimeComponentClients.timerClient,
            sdkExecutionContext,
            sdkTracerFactory,
            serializer,
            ComponentDescriptorFactory.findIgnore(consumerClass),
            ComponentDescriptor.descriptorFor(consumerClass, serializer),
            regionInfo)
        consumerDescriptors :+=
          new ConsumerDescriptor(componentId, clz.getName, consumerSrc, consumerDestination(consumerClass), consumerSpi)

      case clz if classOf[Agent].isAssignableFrom(clz) =>
        val componentId = clz.getAnnotation(classOf[ComponentId]).value
        val agentDescription = Option(clz.getAnnotation(classOf[AgentDescription]))

        val agentClass = clz.asInstanceOf[Class[Agent]]

        val instanceFactory: SpiAgent.FactoryContext => SpiAgent = { factoryContext =>
          new AgentImpl(
            componentId,
            factoryContext.sessionId,
            context =>
              wiredInstance(agentClass) {
                (sideEffectingComponentInjects(None)).orElse {
                  // remember to update component type API doc and docs if changing the set of injectables
                  case p if p == classOf[AgentContext] => context
                }
              },
            sdkExecutionContext,
            sdkTracerFactory,
            serializer,
            ComponentDescriptor.descriptorFor(agentClass, serializer),
            regionInfo,
            new PromptTemplateClient(componentClient(None)),
            componentClient(None),
            applicationConfig)

        }
        agentDescriptors :+=
          new AgentDescriptor(componentId, clz.getName, instanceFactory)

        agentRegistryInfo :+=
          (agentDescription match {
            case Some(desc) =>
              AgentRegistryImpl.AgentDetails(componentId, desc.name, desc.description, desc.role, agentClass)
            case None =>
              // defaults if AgentDescription is not defined
              AgentRegistryImpl.AgentDetails(componentId, name = componentId, description = "", role = "", agentClass)
          })

      case clz if classOf[View].isAssignableFrom(clz) =>
        viewDescriptors :+= ViewDescriptorFactory(clz, serializer, regionInfo, sdkExecutionContext)

      case clz if Reflect.isRestEndpoint(clz) =>
      // handled separately because ComponentId is not mandatory

      case clz =>
        // some other class with @ComponentId annotation
        logger.warn("Unknown component [{}]", clz.getName)
    }

  // these are available for injecting in all kinds of component that are primarily
  // for side effects
  // Note: config is also always available through the combination with user DI way down below
  private def sideEffectingComponentInjects(span: Option[Span]): PartialFunction[Class[_], Any] = {
    // remember to update component type API doc and docs if changing the set of injectables
    case p if p == classOf[ComponentClient]    => componentClient(span)
    case h if h == classOf[HttpClientProvider] => httpClientProvider(span)
    case g if g == classOf[GrpcClientProvider] => grpcClientProvider(span)
    case t if t == classOf[TimerScheduler]     => timerScheduler(span)
    case m if m == classOf[Materializer]       => sdkMaterializer
    case a if a == classOf[Retries]            => retries
    case r if r == classOf[AgentRegistry]      => agentRegistry
    case e if e == classOf[Executor]           =>
      // The type does not guarantee this is a Java concurrent Executor, but we know it is, since supplied from runtime
      sdkExecutionContext.asInstanceOf[Executor]
  }

  val spiComponents: SpiComponents = {

    val serviceSetup: Option[ServiceSetup] = maybeServiceClass match {
      case Some(serviceClassClass) if classOf[ServiceSetup].isAssignableFrom(serviceClassClass) =>
        // FIXME: HttpClientProvider will inject but not quite work for cross service calls until we
        //        pass auth headers with the runner startup context from the runtime
        Some(
          wiredInstance[ServiceSetup](serviceClassClass.asInstanceOf[Class[ServiceSetup]])(
            sideEffectingComponentInjects(None)))
      case _ => None
    }

    // service setup + integration test config
    val combinedDisabledComponents =
      (serviceSetup.map(_.disabledComponents().asScala.toSet).getOrElse(Set.empty) ++ disabledComponents).map(_.getName)

    val descriptors =
      (eventSourcedEntityDescriptors ++
        keyValueEntityDescriptors ++
        httpEndpointDescriptors ++
        grpcEndpointDescriptors ++
        timedActionDescriptors ++
        consumerDescriptors ++
        viewDescriptors ++
        workflowDescriptors ++
        agentDescriptors ++
        mcpEndpoints)
        .filterNot(isDisabled(combinedDisabledComponents))

    val preStart = { (_: ActorSystem[_]) =>
      serviceSetup match {
        case None =>
          startedPromise.trySuccess(
            StartupContext(
              runtimeComponentClients,
              None,
              httpClientProvider,
              grpcClientProvider,
              agentRegistry,
              serializer))
          Future.successful(Done)
        case Some(setup) =>
          if (dependencyProviderOpt.nonEmpty) {
            logger.info("Service configured with TestKit DependencyProvider")
          } else {
            dependencyProviderOpt = Option(setup.createDependencyProvider())
            dependencyProviderOpt.foreach(_ => logger.info("Service configured with DependencyProvider"))
          }
          startedPromise.trySuccess(
            StartupContext(
              runtimeComponentClients,
              dependencyProviderOpt,
              httpClientProvider,
              grpcClientProvider,
              agentRegistry,
              serializer))
          Future.successful(Done)
      }
    }

    val onStart = { _: ActorSystem[_] =>
      serviceSetup match {
        case None => Future.successful(Done)
        case Some(setup) =>
          logger.debug("Running onStart lifecycle hook")
          setup.onStartup()
          Future.successful(Done)
      }
    }

    val reportError = { err: UserFunctionError =>
      val severityString = err.severity match {
        case Level.ERROR => "Error"
        case Level.WARN  => "Warning"
        case Level.INFO  => "Info"
        case Level.DEBUG => "Debug"
        case Level.TRACE => "Trace"
        case other       => other.name()
      }
      val message = s"$severityString reported from Akka runtime: ${err.code} ${err.message}"
      val detail = if (err.detail.isEmpty) Nil else List(err.detail)
      val seeDocs = DocLinks.forErrorCode(err.code).map(link => s"See documentation: $link").toList
      val messages = message :: detail ::: seeDocs
      val logMessage = messages.mkString("\n")

      SdkRunner.userServiceLog.atLevel(err.severity).log(logMessage)

      SdkRunner.FutureDone
    }

    new SpiComponents(
      serviceInfo = new SpiServiceInfo(
        serviceName = serviceNameOverride.orElse(sdkSettings.devModeSettings.map(_.serviceName)).getOrElse(""),
        sdkName = "java",
        sdkVersion = BuildInfo.version,
        protocolMajorVersion = BuildInfo.protocolMajorVersion,
        protocolMinorVersion = BuildInfo.protocolMinorVersion),
      componentDescriptors = descriptors,
      preStart = preStart,
      onStart = onStart,
      reportError = reportError,
      healthCheck = () => SdkRunner.FutureDone)
  }

  private lazy val agentRegistry =
    new AgentRegistryImpl(agentRegistryInfo.toSet, serializer)

  private def isDisabled(disabledComponents: Set[String])(componentDescriptor: spi.ComponentDescriptor): Boolean = {
    val className = componentDescriptor.implementationName
    if (disabledComponents.contains(className)) {
      logger.info("Ignoring component [{}] as it is disabled", className)
      true
    } else
      false
  }

  private def httpEndpointFactory[E](httpEndpointClass: Class[E]): HttpEndpointConstructionContext => E = {
    (context: HttpEndpointConstructionContext) =>
      lazy val requestContext = new RequestContext {
        override def getPrincipals: Principals =
          PrincipalsImpl(context.principal.source, context.principal.service)

        override def getJwtClaims: JwtClaims =
          context.jwt match {
            case Some(jwtClaims) => new JwtClaimsImpl(jwtClaims)
            case None =>
              throw new RuntimeException(
                "There are no JWT claims defined but trying accessing the JWT claims. The class or the method needs to be annotated with @JWT.")
          }

        override def requestHeader(headerName: String): Optional[HttpHeader] =
          // Note: force cast to Java header model
          context.requestHeaders.header(headerName).asInstanceOf[Option[HttpHeader]].toJava

        override def allRequestHeaders(): util.List[HttpHeader] =
          // Note: force cast to Java header model
          context.requestHeaders.allHeaders.asInstanceOf[Seq[HttpHeader]].asJava

        override def tracing(): Tracing = new SpanTracingImpl(context.openTelemetrySpan, sdkTracerFactory)

        override def queryParams(): QueryParams = {
          QueryParamsImpl(context.httpRequest.uri.query())
        }

        override def selfRegion(): String = regionInfo.selfRegion
      }
      val instance = wiredInstance(httpEndpointClass) {
        sideEffectingComponentInjects(context.openTelemetrySpan).orElse {
          case p if p == classOf[RequestContext] => requestContext
        }
      }
      instance match {
        case withBaseClass: AbstractHttpEndpoint => withBaseClass._internalSetRequestContext(requestContext)
        case _                                   =>
      }
      instance
  }

  private def grpcEndpointFactory[E](grpcEndpointClass: Class[E]): GrpcEndpointRequestConstructionContext => E =
    (context: GrpcEndpointRequestConstructionContext) => {

      lazy val grpcRequestContext = new GrpcRequestContext {
        override def getPrincipals: Principals =
          PrincipalsImpl(context.principal.source, context.principal.service)

        override def getJwtClaims: JwtClaims =
          context.jwt match {
            case Some(jwtClaims) => new JwtClaimsImpl(jwtClaims)
            case None =>
              throw new RuntimeException(
                "There are no JWT claims defined but trying accessing the JWT claims. The class or the method needs to be annotated with @JWT.")
          }

        override def metadata(): Metadata = new JavaMetadataImpl(context.metadata)

        override def tracing(): Tracing = new SpanTracingImpl(context.openTelemetrySpan, sdkTracerFactory)

        override def selfRegion(): String = regionInfo.selfRegion
      }

      val instance = wiredInstance(grpcEndpointClass) {
        sideEffectingComponentInjects(context.openTelemetrySpan).orElse {
          case p if p == classOf[GrpcRequestContext] => grpcRequestContext
        }
      }
      instance match {
        case withBaseClass: AbstractGrpcEndpoint => withBaseClass._internalSetRequestContext(grpcRequestContext)
        case _                                   =>
      }
      instance
    }

  private def mcpEndpointFactory[E](mcpEndpointClass: Class[E]): McpEndpointConstructionContext => E = {
    (context: McpEndpointConstructionContext) =>
      lazy val mcpRequestContext = new McpRequestContext {
        override def getPrincipals: Principals =
          PrincipalsImpl(context.principal.source, context.principal.service)

        override def getJwtClaims: JwtClaims =
          context.jwt match {
            case Some(jwtClaims) => new JwtClaimsImpl(jwtClaims)
            case None =>
              throw new RuntimeException(
                "There are no JWT claims defined but trying accessing the JWT claims. The class or the method needs to be annotated with @JWT.")
          }

        override def tracing(): Tracing = new SpanTracingImpl(context.openTelemetrySpan, sdkTracerFactory)

        override def requestHeader(headerName: String): Optional[HttpHeader] =
          // Note: force cast to Java header model
          context.requestHeaders.header(headerName).asInstanceOf[Option[HttpHeader]].toJava

        override def allRequestHeaders(): util.List[HttpHeader] =
          // Note: force cast to Java header model
          context.requestHeaders.allHeaders.asInstanceOf[Seq[HttpHeader]].asJava

      }

      val instance = wiredInstance(mcpEndpointClass) {
        sideEffectingComponentInjects(context.openTelemetrySpan).orElse {
          case p if p == classOf[GrpcRequestContext] => mcpRequestContext
        }
      }
      instance match {
        case withBaseClass: AbstractMcpEndpoint => withBaseClass._internalSetRequestContext(mcpRequestContext)
        case _                                  =>
      }
      instance
  }

  private def wiredInstance[T](clz: Class[T])(partial: PartialFunction[Class[_], Any]): T = {
    // only one constructor allowed
    require(clz.getDeclaredConstructors.length == 1, s"Class [${clz.getSimpleName}] must have only one constructor.")
    wiredInstance(clz.getDeclaredConstructors.head.asInstanceOf[Constructor[T]])(partial)
  }

  /**
   * Create an instance using the passed `constructor` and the mappings defined in `partial`.
   *
   * Each component provider should define what are the acceptable dependencies in the partial function.
   *
   * If the partial function doesn't match, it will try to lookup from a user provided DependencyProvider.
   */
  private def wiredInstance[T](constructor: Constructor[T])(partial: PartialFunction[Class[_], Any]): T = {

    // Note that this function is total because it will always return a value (even if null)
    // last case is a catch all that lookups in the applicationContext
    val totalWireFunction: PartialFunction[Class[_], Any] =
      partial.orElse {
        case p if p == classOf[Config] =>
          userServiceConfig

        // block wiring of clients into anything that is not an Action or Workflow
        // NOTE: if they are allowed, 'partial' should already have a matching case for them
        // if partial func doesn't match, try to lookup in the applicationContext
        case anyOther =>
          dependencyProviderOpt match {
            case _ if platformManagedDependency(anyOther) =>
              //if we allow for a given dependency we should cover it in the partial function for the component
              throw new RuntimeException(
                s"[${constructor.getDeclaringClass.getName}] are not allowed to have a dependency on ${anyOther.getName}");
            case Some(dependencyProvider) =>
              dependencyProvider.getDependency(anyOther)
            case None =>
              throw new RuntimeException(
                s"Could not inject dependency [${anyOther.getName}] required by [${constructor.getDeclaringClass.getName}] as no DependencyProvider was configured.");
          }

      }

    // all params must be wired so we use 'map' not 'collect'
    val params = constructor.getParameterTypes.map(totalWireFunction)

    try constructor.newInstance(params: _*)
    catch {
      case exc: InvocationTargetException if exc.getCause != null =>
        throw exc.getCause
    }
  }

  private def componentClient(openTelemetrySpan: Option[Span]): ComponentClient = {
    ComponentClientImpl(runtimeComponentClients, serializer, agentRegistry.agentClassById, openTelemetrySpan)(
      sdkExecutionContext,
      system)
  }

  private def timerScheduler(openTelemetrySpan: Option[Span]): TimerScheduler = {
    val metadata = openTelemetrySpan match {
      case None       => MetadataImpl.Empty
      case Some(span) => MetadataImpl.Empty.withTracing(span)
    }
    new TimerSchedulerImpl(runtimeComponentClients.timerClient, metadata)
  }

  private def httpClientProvider(openTelemetrySpan: Option[Span]): HttpClientProvider =
    openTelemetrySpan match {
      case None       => httpClientProvider
      case Some(span) => httpClientProvider.withTraceContext(OtelContext.current().`with`(span))
    }

  private def grpcClientProvider(openTelemetrySpan: Option[Span]): GrpcClientProvider =
    openTelemetrySpan match {
      case None       => grpcClientProvider
      case Some(span) => grpcClientProvider.withTraceContext(OtelContext.current().`with`(span))
    }

}
