/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import java.lang.reflect.Constructor
import java.lang.reflect.ParameterizedType
import java.util.concurrent.CompletionStage

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.jdk.FutureConverters._
import scala.reflect.ClassTag
import scala.util.control.NonFatal

import akka.Done
import akka.actor.typed.ActorSystem
import akka.annotation.InternalApi
import akka.javasdk.BuildInfo
import akka.javasdk.DependencyProvider
import akka.javasdk.ServiceSetup
import akka.javasdk.annotations.ComponentId
import akka.javasdk.annotations.Setup
import akka.javasdk.annotations.http.HttpEndpoint
import akka.javasdk.client.ComponentClient
import akka.javasdk.consumer.Consumer
import akka.javasdk.consumer.ConsumerContext
import akka.javasdk.eventsourcedentity.EventSourcedEntity
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext
import akka.javasdk.http.HttpClientProvider
import akka.javasdk.impl.Sdk.StartupContext
import akka.javasdk.impl.Validations.Invalid
import akka.javasdk.impl.Validations.Valid
import akka.javasdk.impl.Validations.Validation
import akka.javasdk.impl.action.ActionService
import akka.javasdk.impl.action.ActionsImpl
import akka.javasdk.impl.client.ComponentClientImpl
import akka.javasdk.impl.consumer.ConsumerProvider
import akka.javasdk.impl.consumer.ConsumerService
import akka.javasdk.impl.eventsourcedentity.EventSourcedEntitiesImpl
import akka.javasdk.impl.eventsourcedentity.EventSourcedEntityProvider
import akka.javasdk.impl.eventsourcedentity.EventSourcedEntityService
import akka.javasdk.impl.http.HttpClientProviderImpl
import akka.javasdk.impl.keyvalueentity.KeyValueEntitiesImpl
import akka.javasdk.impl.keyvalueentity.KeyValueEntityProvider
import akka.javasdk.impl.keyvalueentity.KeyValueEntityService
import akka.javasdk.impl.reflection.Reflect
import akka.javasdk.impl.reflection.Reflect.Syntax.AnnotatedElementOps
import akka.javasdk.impl.timedaction.TimedActionProvider
import akka.javasdk.impl.timer.TimerSchedulerImpl
import akka.javasdk.impl.view.ViewProvider
import akka.javasdk.impl.view.ViewService
import akka.javasdk.impl.view.ViewsImpl
import akka.javasdk.impl.workflow.WorkflowImpl
import akka.javasdk.impl.workflow.WorkflowProvider
import akka.javasdk.impl.workflow.WorkflowService
import akka.javasdk.keyvalueentity.KeyValueEntity
import akka.javasdk.keyvalueentity.KeyValueEntityContext
import akka.javasdk.timedaction.TimedAction
import akka.javasdk.timedaction.TimedActionContext
import akka.javasdk.timer.TimerScheduler
import akka.javasdk.view.TableUpdater
import akka.javasdk.view.View
import akka.javasdk.view.ViewContext
import akka.javasdk.workflow.Workflow
import akka.javasdk.workflow.WorkflowContext
import akka.runtime.sdk.spi.ComponentClients
import akka.runtime.sdk.spi.HttpEndpointConstructionContext
import akka.runtime.sdk.spi.HttpEndpointDescriptor
import akka.runtime.sdk.spi.SpiComponents
import akka.runtime.sdk.spi.SpiDevModeSettings
import akka.runtime.sdk.spi.SpiEventingSupportSettings
import akka.runtime.sdk.spi.SpiMockedEventingSettings
import akka.runtime.sdk.spi.SpiSettings
import akka.runtime.sdk.spi.StartContext
import akka.stream.Materializer
import com.google.protobuf.Descriptors
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import kalix.protocol.action.Actions
import kalix.protocol.discovery.Discovery
import kalix.protocol.event_sourced_entity.EventSourcedEntities
import kalix.protocol.replicated_entity.ReplicatedEntities
import kalix.protocol.value_entity.ValueEntities
import kalix.protocol.view.Views
import kalix.protocol.workflow_entity.WorkflowEntities
import org.slf4j.LoggerFactory

/**
 * INTERNAL API
 */
@InternalApi
class SdkRunner extends akka.runtime.sdk.spi.Runner {
  private val startedPromise = Promise[StartupContext]()

  def applicationConfig: Config =
    ApplicationConfig.loadApplicationConf

  override def getSettings: SpiSettings = {
    val applicationConf = applicationConfig
    val devModeSettings =
      if (applicationConf.getBoolean("akka.javasdk.dev-mode.enabled"))
        Some(
          new SpiDevModeSettings(
            httpPort = applicationConf.getInt("akka.javasdk.dev-mode.http-port"),
            aclEnabled = applicationConf.getBoolean("akka.javasdk.dev-mode.acl.enabled"),
            serviceName = applicationConf.getString("akka.javasdk.dev-mode.service-name"),
            eventingSupport = SpiEventingSupportSettings.fromConfigValue(
              applicationConf.getString("akka.javasdk.dev-mode.eventing.support")),
            mockedEventing = SpiMockedEventingSettings.empty,
            testMode = false))
      else
        None

    new SpiSettings(devModeSettings)
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
        startedPromise)
      Future.successful(app.spiEndpoints)
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
        "http-endpoint" -> classOf[AnyRef],
        "timed-action" -> classOf[TimedAction],
        "consumer" -> classOf[Consumer],
        "event-sourced-entity" -> classOf[EventSourcedEntity[_, _]],
        "workflow" -> classOf[Workflow[_]],
        "key-value-entity" -> classOf[KeyValueEntity[_]],
        "view" -> classOf[AnyRef])

    // Alternative to but inspired by the stdlib SPI style of registering in META-INF/services
    // since we don't always have top supertypes and want to inject things into component constructors
    logger.info("Looking for component descriptors in [{}]", ComponentDescriptorResourcePath)

    // Descriptor hocon has one entry per component type with a list of strings containing
    // the concrete component classes for the given project
    val descriptorConfig = ConfigFactory.load(ComponentDescriptorResourcePath)
    val componentConfig = descriptorConfig.getConfig(DescriptorComponentBasePath)

    val components = kalixComponentTypeAndBaseClasses.flatMap { case (componentTypeKey, componentTypeClass) =>
      if (componentConfig.hasPath(componentTypeKey))
        componentConfig.getStringList(componentTypeKey).asScala.map { className =>
          val componentClass = system.dynamicAccess.getClassFor(className)(ClassTag(componentTypeClass)).get
          logger.debug("Found and loaded component class: [{}]", componentClass)
          componentClass
        }
      else
        Seq.empty
    }.toSeq

    if (descriptorConfig.hasPath(DescriptorServiceSetupEntryPath)) {
      // central config/lifecycle class
      val serviceSetupClassName = descriptorConfig.getString(DescriptorServiceSetupEntryPath)
      val serviceSetup = system.dynamicAccess.getClassFor[AnyRef](serviceSetupClassName).get
      if (serviceSetup.hasAnnotation[Setup]) {
        logger.debug("Found and loaded service class setup: [{}]", serviceSetup)
      } else {
        logger.warn(
          "Ignoring service class [{}] as it does not have the the @PlatformServiceSetup annotation",
          serviceSetup)
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
  final case class StartupContext(componentClients: ComponentClients, dependencyProvider: Option[DependencyProvider])
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
    startedPromise: Promise[StartupContext]) {
  private val logger = LoggerFactory.getLogger(getClass)
  private val messageCodec = new JsonMessageCodec
  private val ComponentLocator.LocatedClasses(componentClasses, maybeServiceClass) =
    ComponentLocator.locateUserComponents(system)
  private var dependencyProviderOpt: Option[DependencyProvider] = None

  private val applicationConfig = ApplicationConfig(system).getConfig
  private val sdkSettings = Settings(applicationConfig.getConfig("akka.javasdk"))

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
    case Invalid(messages) =>
      messages.foreach { msg => logger.error(msg) }
      validation.failIfInvalid
  }

  // register them if all valid, prototobuf
  private val componentFactories: Map[Descriptors.ServiceDescriptor, () => Service] = componentClasses
    .filter(hasComponentId)
    .foldLeft(Map[Descriptors.ServiceDescriptor, () => Service]()) { (factories, clz) =>
      if (classOf[TimedAction].isAssignableFrom(clz)) {
        logger.info(s"Registering TimedAction provider for [${clz.getName}]")
        val action = timedActionProvider(clz.asInstanceOf[Class[TimedAction]])
        factories.updated(action.serviceDescriptor, action.newServiceInstance _)
      } else if (classOf[Consumer].isAssignableFrom(clz)) {
        logger.info(s"Registering Consumer provider for [${clz.getName}]")
        val consumer = consumerProvider(clz.asInstanceOf[Class[Consumer]])
        factories.updated(consumer.serviceDescriptor, consumer.newServiceInstance _)
      } else if (classOf[EventSourcedEntity[_, _]].isAssignableFrom(clz)) {
        logger.info(s"Registering EventSourcedEntity provider for [${clz.getName}]")
        val esEntity = eventSourcedEntityProvider(clz.asInstanceOf[Class[EventSourcedEntity[Nothing, Nothing]]])
        factories.updated(esEntity.serviceDescriptor, esEntity.newServiceInstance _)
      } else if (classOf[Workflow[_]].isAssignableFrom(clz)) {
        logger.info(s"Registering Workflow provider for [${clz.getName}]")
        val workflow = workflowProvider(clz.asInstanceOf[Class[Workflow[Nothing]]])
        factories.updated(workflow.serviceDescriptor, workflow.newServiceInstance _)
      } else if (classOf[KeyValueEntity[_]].isAssignableFrom(clz)) {
        logger.info(s"Registering KeyValueEntity provider for [${clz.getName}]")
        val keyValueEntity = valueEntityProvider(clz.asInstanceOf[Class[KeyValueEntity[Nothing]]])
        factories.updated(keyValueEntity.serviceDescriptor, keyValueEntity.newServiceInstance _)
      } else if (Reflect.isView(clz)) {
        logger.info(s"Registering View provider for [${clz.getName}]")
        val view = viewProvider(clz.asInstanceOf[Class[View]])
        factories.updated(view.serviceDescriptor, view.newServiceInstance _)
      } else throw new IllegalArgumentException(s"Component class of unknown component type [$clz]")
    }

  private def hasComponentId(clz: Class[_]): Boolean = {
    if (clz.hasAnnotation[ComponentId]) {
      true
    } else {
      //additional check to skip logging for endpoints
      if (!clz.hasAnnotation[HttpEndpoint]) {
        //this could happened when we remove the @ComponentId annotation from the class,
        //the file descriptor generated by annotation processor might still have this class entry,
        //for instance when working with IDE and incremental compilation (without clean)
        logger.warn("Ignoring component [{}] as it does not have the @ComponentId annotation", clz.getName)
      }
      false
    }
  }

  // collect all Endpoints and compose them to build a larger router
  private val httpEndpoints = componentClasses
    .filter(Reflect.isRestEndpoint)
    .map { httpEndpointClass =>
      HttpEndpointDescriptorFactory(httpEndpointClass, httpEndpointFactory(httpEndpointClass))
    }

  // FIXME mixing runtime config with sdk with user project config is tricky
  def spiEndpoints: SpiComponents = {

    var actionsEndpoint: Option[Actions] = None
    var eventSourcedEntitiesEndpoint: Option[EventSourcedEntities] = None
    var valueEntitiesEndpoint: Option[ValueEntities] = None
    var viewsEndpoint: Option[Views] = None
    var workflowEntitiesEndpoint: Option[WorkflowEntities] = None

    val classicSystem = system.classicSystem

    val services = componentFactories.map { case (serviceDescriptor, factory) =>
      serviceDescriptor.getFullName -> factory()
    }

    val actionAndConsumerServices = services.filter { case (_, service) =>
      service.getClass == classOf[ActionService] || service.getClass == classOf[ConsumerService]
    }

    if (actionAndConsumerServices.nonEmpty) {
      actionsEndpoint = Some(
        new ActionsImpl(
          classicSystem,
          actionAndConsumerServices,
          runtimeComponentClients.timerClient,
          sdkExecutionContext))
    }

    services.groupBy(_._2.getClass).foreach {

      case (serviceClass, eventSourcedServices: Map[String, EventSourcedEntityService] @unchecked)
          if serviceClass == classOf[EventSourcedEntityService] =>
        val eventSourcedImpl =
          new EventSourcedEntitiesImpl(classicSystem, eventSourcedServices, sdkSettings, sdkDispatcherName)
        eventSourcedEntitiesEndpoint = Some(eventSourcedImpl)

      case (serviceClass, entityServices: Map[String, KeyValueEntityService] @unchecked)
          if serviceClass == classOf[KeyValueEntityService] =>
        valueEntitiesEndpoint =
          Some(new KeyValueEntitiesImpl(classicSystem, entityServices, sdkSettings, sdkDispatcherName))

      case (serviceClass, workflowServices: Map[String, WorkflowService] @unchecked)
          if serviceClass == classOf[WorkflowService] =>
        workflowEntitiesEndpoint = Some(
          new WorkflowImpl(
            classicSystem,
            workflowServices,
            runtimeComponentClients.timerClient,
            sdkExecutionContext,
            sdkDispatcherName))

      case (serviceClass, _: Map[String, ActionService] @unchecked) if serviceClass == classOf[ActionService] =>
      //ignore

      case (serviceClass, _: Map[String, ConsumerService] @unchecked) if serviceClass == classOf[ConsumerService] =>
      //ignore

      case (serviceClass, viewServices: Map[String, ViewService] @unchecked) if serviceClass == classOf[ViewService] =>
        viewsEndpoint = Some(new ViewsImpl(classicSystem, viewServices, sdkDispatcherName))

      case (serviceClass, _) =>
        sys.error(s"Unknown service type: $serviceClass")
    }

    val serviceSetup: Option[ServiceSetup] = maybeServiceClass match {
      case Some(serviceClassClass) if classOf[ServiceSetup].isAssignableFrom(serviceClassClass) =>
        // FIXME: HttpClient is tricky because auth headers waiting for discovery, we could maybe sort that
        //        by passing more runtime metadata as parameters to the start call?
        Some(wiredInstance[ServiceSetup](serviceClassClass.asInstanceOf[Class[ServiceSetup]]) {
          case p if p == classOf[ComponentClient] => componentClient()
          case t if t == classOf[TimerScheduler]  => timerScheduler()
          case c if c == classOf[Config]          => userServiceConfig
          case m if m == classOf[Materializer]    => sdkMaterializer
        })
      case _ => None
    }

    val devModeServiceName = sdkSettings.devModeSettings.map(_.serviceName)
    val aclDescriptor =
      maybeServiceClass
        .map(serviceClass => AclDescriptorFactory.buildAclFileDescriptor(serviceClass))
        .getOrElse(AclDescriptorFactory.defaultAclFileDescriptor)
    val discoveryEndpoint =
      new DiscoveryImpl(classicSystem, services, aclDescriptor, BuildInfo.name, devModeServiceName)

    new SpiComponents {
      override def preStart(system: ActorSystem[_]): Future[Done] = {
        serviceSetup match {
          case None =>
            startedPromise.trySuccess(StartupContext(runtimeComponentClients, None))
            Future.successful(Done)
          case Some(setup) =>
            dependencyProviderOpt = Option(setup.createDependencyProvider())
            dependencyProviderOpt.foreach(_ => logger.info("Service configured with DependencyProvider"))
            startedPromise.trySuccess(StartupContext(runtimeComponentClients, dependencyProviderOpt))
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

      override def discovery: Discovery = discoveryEndpoint
      override def actions: Option[Actions] = actionsEndpoint
      override def eventSourcedEntities: Option[EventSourcedEntities] = eventSourcedEntitiesEndpoint
      override def valueEntities: Option[ValueEntities] = valueEntitiesEndpoint
      override def views: Option[Views] = viewsEndpoint
      override def workflowEntities: Option[WorkflowEntities] = workflowEntitiesEndpoint
      override def replicatedEntities: Option[ReplicatedEntities] = None
      override def httpEndpointDescriptors: Seq[HttpEndpointDescriptor] = httpEndpoints
    }
  }

  private def timedActionProvider[A <: TimedAction](clz: Class[A]): TimedActionProvider[A] =
    TimedActionProvider(
      clz,
      messageCodec,
      context =>
        wiredInstance(clz) {
          case p if p == classOf[TimedActionContext] => context
          case p if p == classOf[ComponentClient]    => componentClient()
          case h if h == classOf[HttpClientProvider] => httpClientProvider()
          case t if t == classOf[TimerScheduler]     => timerScheduler()
          case m if m == classOf[Materializer]       => sdkMaterializer
        })

  private def consumerProvider[A <: Consumer](clz: Class[A]): ConsumerProvider[A] =
    ConsumerProvider(
      clz,
      messageCodec,
      context =>
        wiredInstance(clz) {
          case p if p == classOf[ConsumerContext]    => context
          case p if p == classOf[ComponentClient]    => componentClient()
          case h if h == classOf[HttpClientProvider] => httpClientProvider()
          case t if t == classOf[TimerScheduler]     => timerScheduler()
          case m if m == classOf[Materializer]       => sdkMaterializer
        })

  private def workflowProvider[S, W <: Workflow[S]](clz: Class[W]): WorkflowProvider[S, W] = {
    WorkflowProvider(
      clz,
      messageCodec,
      { context =>

        val workflow =
          wiredInstance(clz) {
            case p if p == classOf[WorkflowContext]    => context
            case p if p == classOf[ComponentClient]    => componentClient()
            case h if h == classOf[HttpClientProvider] => httpClientProvider()
            case m if m == classOf[Materializer]       => sdkMaterializer
          }

        val workflowStateType: Class[S] =
          workflow.getClass.getGenericSuperclass
            .asInstanceOf[ParameterizedType]
            .getActualTypeArguments
            .head
            .asInstanceOf[Class[S]]

        messageCodec.registerTypeHints(workflowStateType)

        workflow
          .definition()
          .getSteps
          .asScala
          .flatMap {
            case asyncCallStep: Workflow.AsyncCallStep[_, _, _] =>
              List(asyncCallStep.callInputClass, asyncCallStep.transitionInputClass)
            case callStep: Workflow.CallStep[_, _, _, _] =>
              List(callStep.callInputClass, callStep.transitionInputClass)
          }
          .foreach(messageCodec.registerTypeHints)

        workflow
      })
  }

  private def eventSourcedEntityProvider[S, E, ES <: EventSourcedEntity[S, E]](
      clz: Class[ES]): EventSourcedEntityProvider[S, E, ES] =
    EventSourcedEntityProvider(
      clz,
      messageCodec,
      context =>
        wiredInstance(clz) {
          case p if p == classOf[EventSourcedEntityContext] => context
        })

  private def valueEntityProvider[S, VE <: KeyValueEntity[S]](clz: Class[VE]): KeyValueEntityProvider[S, VE] =
    KeyValueEntityProvider(
      clz,
      messageCodec,
      context =>
        wiredInstance(clz) {
          case p if p == classOf[KeyValueEntityContext] => context
        })

  // FIXME a bit much of low level stuff here right now
  private def viewProvider[V <: View](clz: Class[V]): ViewProvider[V] =
    ViewProvider[V](
      clz,
      messageCodec,
      ComponentDescriptorFactory.readComponentIdIdValue(clz),
      { context =>
        val updaterClasses = clz.getDeclaredClasses.collect {
          case clz if Reflect.isViewTableUpdater(clz) => clz.asInstanceOf[Class[TableUpdater[AnyRef]]]
        }.toSet
        updaterClasses.map(updaterClass =>
          wiredInstance(updaterClass) {
            case p if p == classOf[ViewContext] => context
          })
      })

  private def httpEndpointFactory[E](httpEndpointClass: Class[E]): HttpEndpointConstructionContext => E = {
    (context: HttpEndpointConstructionContext) =>
      wiredInstance(httpEndpointClass) {
        case p if p == classOf[ComponentClient]    => componentClient(context.openTelemetrySpan)
        case s if s == classOf[Span]               => context.openTelemetrySpan.getOrElse(Span.current())
        case h if h == classOf[HttpClientProvider] => httpClientProvider(context.openTelemetrySpan)
        case h if h == classOf[TimerScheduler]     => timerScheduler(context.openTelemetrySpan)
        case m if m == classOf[Materializer]       => sdkMaterializer
      }
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
   * If the partial function doesn't match, it will try to lookup in the Spring applicationContext.
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

    constructor.newInstance(params: _*)
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
    anyOther == classOf[KeyValueEntityContext] ||
    anyOther == classOf[ViewContext]
  }

  private def componentClient(openTelemetrySpan: Option[Span] = None): ComponentClient = {
    ComponentClientImpl(runtimeComponentClients, openTelemetrySpan)(sdkExecutionContext)
  }

  private def timerScheduler(openTelemetrySpan: Option[Span] = None): TimerScheduler = {
    val metadata = openTelemetrySpan match {
      case None       => MetadataImpl.Empty
      case Some(span) => MetadataImpl.Empty.withTracing(span)
    }
    new TimerSchedulerImpl(messageCodec, runtimeComponentClients.timerClient, metadata)
  }

  private def httpClientProvider(openTelemetrySpan: Option[Span] = None): HttpClientProvider = {
    val extension = HttpClientProviderImpl(system)
    openTelemetrySpan match {
      case None       => extension
      case Some(span) => extension.withTraceContext(Context.current().`with`(span))
    }
  }

}
