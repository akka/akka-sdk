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

import scala.annotation.nowarn
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
import akka.http.javadsl.model.HttpHeader
import akka.http.scaladsl.model.headers.RawHeader
import akka.javasdk.BuildInfo
import akka.javasdk.DependencyProvider
import akka.javasdk.JwtClaims
import akka.javasdk.Principals
import akka.javasdk.ServiceSetup
import akka.javasdk.Tracing
import akka.javasdk.annotations.ComponentId
import akka.javasdk.annotations.Setup
import akka.javasdk.annotations.http.HttpEndpoint
import akka.javasdk.client.ComponentClient
import akka.javasdk.consumer.Consumer
import akka.javasdk.eventsourcedentity.EventSourcedEntity
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext
import akka.javasdk.http.AbstractHttpEndpoint
import akka.javasdk.http.HttpClientProvider
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
import akka.javasdk.impl.http.HttpClientProviderImpl
import akka.javasdk.impl.http.JwtClaimsImpl
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
import akka.javasdk.workflow.WorkflowContext
import akka.runtime.sdk.spi.ComponentClients
import akka.runtime.sdk.spi.ConsumerDescriptor
import akka.runtime.sdk.spi.EventSourcedEntityDescriptor
import akka.runtime.sdk.spi.HttpEndpointConstructionContext
import akka.runtime.sdk.spi.HttpEndpointDescriptor
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
import akka.runtime.sdk.spi.WorkflowDescriptor
import akka.runtime.sdk.spi.views.SpiViewDescriptor
import akka.stream.Materializer
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.{ Context => OtelContext }
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.event.Level

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
class SdkRunner private (dependencyProvider: Option[DependencyProvider]) extends akka.runtime.sdk.spi.Runner {
  private val startedPromise = Promise[StartupContext]()

  // default constructor for runtime creation
  def this() = this(None)

  // constructor for testkit
  def this(dependencyProvider: java.util.Optional[DependencyProvider]) = this(dependencyProvider.toScala)

  def applicationConfig: Config =
    ApplicationConfig.loadApplicationConf

  @nowarn("msg=deprecated") //TODO remove deprecation once we remove the old constructor
  override def getSettings: SpiSettings = {
    val applicationConf = applicationConfig

    val eventSourcedEntitySnapshotEvery = applicationConfig.getInt("akka.javasdk.event-sourced-entity.snapshot-every")

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

    new SpiSettings(eventSourcedEntitySnapshotEvery, devModeSettings)
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
        startContext.sdkDispatcherName,
        startContext.executionContext,
        startContext.materializer,
        startContext.componentClients,
        startContext.remoteIdentification,
        startContext.tracerFactory,
        dependencyProvider,
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
  val Consumer = "consumer"
  val TimedAction = "timed-action"
  val View = "view"
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
        ComponentType.TimedAction -> classOf[TimedAction],
        ComponentType.Consumer -> classOf[Consumer],
        ComponentType.EventSourcedEntity -> classOf[EventSourcedEntity[_, _]],
        ComponentType.Workflow -> classOf[Workflow[_]],
        ComponentType.KeyValueEntity -> classOf[KeyValueEntity[_]],
        ComponentType.View -> classOf[AnyRef])

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

    val components = kalixComponentTypeAndBaseClasses.flatMap { case (componentTypeKey, componentTypeClass) =>
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

    if (descriptorConfig.hasPath(DescriptorServiceSetupEntryPath)) {
      // central config/lifecycle class
      val serviceSetupClassName = descriptorConfig.getString(DescriptorServiceSetupEntryPath)
      val serviceSetup = system.dynamicAccess.getClassFor[AnyRef](serviceSetupClassName).get
      if (serviceSetup.hasAnnotation[Setup]) {
        logger.debug("Found and loaded service class setup: [{}]", serviceSetup)
      } else {
        logger.warn("Ignoring service class [{}] as it does not have the the @Setup annotation", serviceSetup)
      }
      LocatedClasses(components, Some(serviceSetup))
    } else {
      LocatedClasses(components, None)
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
      serializer: JsonSerializer)
}

/**
 * INTERNAL API
 */
@InternalApi
private final class Sdk(
    system: ActorSystem[_],
    sdkDispatcherName: String,
    sdkExecutionContext: ExecutionContext,
    sdkMaterializer: Materializer,
    runtimeComponentClients: ComponentClients,
    remoteIdentification: Option[RemoteIdentification],
    tracerFactory: String => Tracer,
    dependencyProviderOverride: Option[DependencyProvider],
    startedPromise: Promise[StartupContext],
    serviceNameOverride: Option[String]) {
  private val logger = LoggerFactory.getLogger(getClass)
  private val serializer = new JsonSerializer
  private val ComponentLocator.LocatedClasses(componentClasses, maybeServiceClass) =
    ComponentLocator.locateUserComponents(system)
  @volatile private var dependencyProviderOpt: Option[DependencyProvider] = dependencyProviderOverride

  private val applicationConfig = ApplicationConfig(system).getConfig
  private val sdkSettings = Settings(applicationConfig.getConfig("akka.javasdk"))

  private val sdkTracerFactory = () => tracerFactory(TraceInstrumentation.InstrumentationScopeName)

  private val httpClientProvider = new HttpClientProviderImpl(
    system,
    None,
    remoteIdentification.map(ri => RawHeader(ri.headerName, ri.headerValue)),
    sdkSettings)

  private lazy val userServiceConfig = {
    // hiding these paths from the config provided to user
    val sensitivePaths = List("akka", "kalix.meta", "kalix.proxy", "kalix.runtime", "system")
    val sdkConfig = applicationConfig.getConfig("akka.javasdk")
    sensitivePaths
      .foldLeft(applicationConfig) { (conf, toHide) => conf.withoutPath(toHide) }
      .withFallback(sdkConfig)
  }

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
      if (!clz.hasAnnotation[HttpEndpoint]) {
        //this could happen when we remove the @ComponentId annotation from the class,
        //the file descriptor generated by annotation processor might still have this class entry,
        //for instance when working with IDE and incremental compilation (without clean)
        logger.warn("Ignoring component [{}] as it does not have the @ComponentId annotation", clz.getName)
      }
      false
    }
  }

  private def isNotExcluded(clz: Class[_]): Boolean = {
    val componentName = clz.getName
    if (sdkSettings.excludedComponents.contains(componentName)) {
      logger.info("Ignoring component [{}] as it is excluded in the configuration", clz.getName)
      false
    } else {
      true
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
      serializer,
      ComponentDescriptor.descriptorFor(clz, serializer),
      timerClient = runtimeComponentClients.timerClient,
      sdkExecutionContext,
      sdkTracerFactory,
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
          .flatMap { case asyncCallStep: Workflow.AsyncCallStep[_, _, _] =>
            List(asyncCallStep.callInputClass, asyncCallStep.transitionInputClass)
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

  private var eventSourcedEntityDescriptors = Vector.empty[EventSourcedEntityDescriptor]
  private var keyValueEntityDescriptors = Vector.empty[EventSourcedEntityDescriptor]
  private var workflowDescriptors = Vector.empty[WorkflowDescriptor]
  private var timedActionDescriptors = Vector.empty[TimedActionDescriptor]
  private var consumerDescriptors = Vector.empty[ConsumerDescriptor]

  componentClasses
    .filter(hasComponentId)
    .filter(isNotExcluded)
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
            sdkSettings,
            sdkTracerFactory,
            componentId,
            factoryContext.entityId,
            serializer,
            ComponentDescriptor.descriptorFor(clz, serializer),
            entityStateType,
            context =>
              wiredInstance(clz.asInstanceOf[Class[EventSourcedEntity[AnyRef, AnyRef]]]) {
                // remember to update component type API doc and docs if changing the set of injectables
                case p if p == classOf[EventSourcedEntityContext] => context
              })
        }
        eventSourcedEntityDescriptors :+=
          new EventSourcedEntityDescriptor(componentId, clz.getName, readOnlyCommandNames, instanceFactory)

      case clz if classOf[KeyValueEntity[_]].isAssignableFrom(clz) =>
        val componentId = clz.getAnnotation(classOf[ComponentId]).value

        val readOnlyCommandNames = Set.empty[String]

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
            context =>
              wiredInstance(clz.asInstanceOf[Class[KeyValueEntity[AnyRef]]]) {
                // remember to update component type API doc and docs if changing the set of injectables
                case p if p == classOf[KeyValueEntityContext] => context
              })
        }
        keyValueEntityDescriptors :+=
          new EventSourcedEntityDescriptor(componentId, clz.getName, readOnlyCommandNames, instanceFactory)

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
            ComponentDescriptor.descriptorFor(timedActionClass, serializer))
        timedActionDescriptors :+=
          new TimedActionDescriptor(componentId, clz.getName, timedActionSpi)

      case clz if classOf[Consumer].isAssignableFrom(clz) =>
        val componentId = clz.getAnnotation(classOf[ComponentId]).value
        val consumerClass = clz.asInstanceOf[Class[Consumer]]
        val timedActionSpi =
          new ConsumerImpl[Consumer](
            componentId,
            () => wiredInstance(consumerClass)(sideEffectingComponentInjects(None)),
            consumerClass,
            system.classicSystem,
            runtimeComponentClients.timerClient,
            sdkExecutionContext,
            sdkTracerFactory,
            serializer,
            ComponentDescriptorFactory.findIgnore(consumerClass),
            ComponentDescriptor.descriptorFor(consumerClass, serializer))
        consumerDescriptors :+=
          new ConsumerDescriptor(
            componentId,
            clz.getName,
            consumerSource(consumerClass),
            consumerDestination(consumerClass),
            timedActionSpi)

      case clz if Reflect.isRestEndpoint(clz) =>
      // handled separately because ComponentId is not mandatory

      case clz =>
        // some other class with @ComponentId annotation
        logger.warn("Unknown component [{}]", clz.getName)
    }

  private val viewDescriptors: Seq[SpiViewDescriptor] =
    componentClasses
      .filter(hasComponentId)
      .filter(isNotExcluded)
      .collect {
        case clz if classOf[View].isAssignableFrom(clz) => ViewDescriptorFactory(clz, serializer, sdkExecutionContext)
      }

  // these are available for injecting in all kinds of component that are primarily
  // for side effects
  // Note: config is also always available through the combination with user DI way down below
  private def sideEffectingComponentInjects(span: Option[Span]): PartialFunction[Class[_], Any] = {
    // remember to update component type API doc and docs if changing the set of injectables
    case p if p == classOf[ComponentClient]    => componentClient(span)
    case h if h == classOf[HttpClientProvider] => httpClientProvider(span)
    case t if t == classOf[TimerScheduler]     => timerScheduler(span)
    case m if m == classOf[Materializer]       => sdkMaterializer
  }

  def spiComponents: SpiComponents = {

    val serviceSetup: Option[ServiceSetup] = maybeServiceClass match {
      case Some(serviceClassClass) if classOf[ServiceSetup].isAssignableFrom(serviceClassClass) =>
        // FIXME: HttpClientProvider will inject but not quite work for cross service calls until we
        //        pass auth headers with the runner startup context from the runtime
        Some(
          wiredInstance[ServiceSetup](serviceClassClass.asInstanceOf[Class[ServiceSetup]])(
            sideEffectingComponentInjects(None)))
      case _ => None
    }

    new SpiComponents {
      override def preStart(system: ActorSystem[_]): Future[Done] = {
        serviceSetup match {
          case None =>
            startedPromise.trySuccess(StartupContext(runtimeComponentClients, None, httpClientProvider, serializer))
            Future.successful(Done)
          case Some(setup) =>
            if (dependencyProviderOpt.nonEmpty) {
              logger.info("Service configured with TestKit DependencyProvider")
            } else {
              dependencyProviderOpt = Option(setup.createDependencyProvider())
              dependencyProviderOpt.foreach(_ => logger.info("Service configured with DependencyProvider"))
            }
            startedPromise.trySuccess(
              StartupContext(runtimeComponentClients, dependencyProviderOpt, httpClientProvider, serializer))
            Future.successful(Done)
        }
      }

      override def onStart(system: ActorSystem[_]): Future[Done] = {

        serviceSetup match {
          case None => Future.successful(Done)
          case Some(setup) =>
            logger.debug("Running onStart lifecycle hook")
            setup.onStartup()
            Future.successful(Done)
        }
      }

      override val eventSourcedEntityDescriptors: Seq[EventSourcedEntityDescriptor] =
        Sdk.this.eventSourcedEntityDescriptors

      override val keyValueEntityDescriptors: Seq[EventSourcedEntityDescriptor] =
        Sdk.this.keyValueEntityDescriptors

      override val httpEndpointDescriptors: Seq[HttpEndpointDescriptor] =
        Sdk.this.httpEndpointDescriptors

      override val timedActionsDescriptors: Seq[TimedActionDescriptor] =
        Sdk.this.timedActionDescriptors

      override val consumersDescriptors: Seq[ConsumerDescriptor] =
        Sdk.this.consumerDescriptors

      override val viewDescriptors: Seq[SpiViewDescriptor] =
        Sdk.this.viewDescriptors

      override val workflowDescriptors: Seq[WorkflowDescriptor] =
        Sdk.this.workflowDescriptors

      override val serviceInfo: SpiServiceInfo =
        new SpiServiceInfo(
          serviceName = serviceNameOverride.orElse(sdkSettings.devModeSettings.map(_.serviceName)).getOrElse(""),
          sdkName = "java",
          sdkVersion = BuildInfo.version,
          protocolMajorVersion = BuildInfo.protocolMajorVersion,
          protocolMinorVersion = BuildInfo.protocolMinorVersion)

      override def reportError(err: UserFunctionError): Future[Done] = {
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

      override def healthCheck(): Future[Done] =
        SdkRunner.FutureDone

    }
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

  private def platformManagedDependency(anyOther: Class[_]) = {
    anyOther == classOf[ComponentClient] ||
    anyOther == classOf[TimerScheduler] ||
    anyOther == classOf[HttpClientProvider] ||
    anyOther == classOf[Tracer] ||
    anyOther == classOf[Span] ||
    anyOther == classOf[Config] ||
    anyOther == classOf[WorkflowContext] ||
    anyOther == classOf[EventSourcedEntityContext] ||
    anyOther == classOf[KeyValueEntityContext]
  }

  private def componentClient(openTelemetrySpan: Option[Span]): ComponentClient = {
    ComponentClientImpl(runtimeComponentClients, serializer, openTelemetrySpan)(sdkExecutionContext)
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

}
