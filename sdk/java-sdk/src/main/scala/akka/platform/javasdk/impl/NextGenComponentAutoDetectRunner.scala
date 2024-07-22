/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.impl

import java.lang.reflect.Constructor
import java.lang.reflect.ParameterizedType
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference

import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.jdk.CollectionConverters.MapHasAsScala
import scala.jdk.DurationConverters.JavaDurationOps
import scala.reflect.ClassTag
import scala.util.control.NonFatal

import akka.Done
import akka.actor.typed.ActorSystem
import akka.annotation.InternalApi
import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse
import akka.platform.javasdk.BuildInfo
import akka.platform.javasdk.Kalix
import akka.platform.javasdk.ServiceLifecycle
import akka.platform.javasdk.action.Action
import akka.platform.javasdk.action.ActionCreationContext
import akka.platform.javasdk.action.ActionProvider
import akka.platform.javasdk.action.ReflectiveActionProvider
import akka.platform.javasdk.client.ComponentClient
import akka.platform.javasdk.eventsourcedentity.EventSourcedEntity
import akka.platform.javasdk.eventsourcedentity.EventSourcedEntityContext
import akka.platform.javasdk.eventsourcedentity.EventSourcedEntityProvider
import akka.platform.javasdk.eventsourcedentity.ReflectiveEventSourcedEntityProvider
import akka.platform.javasdk.http.HttpClientProvider
import akka.platform.javasdk.impl.Validations.Invalid
import akka.platform.javasdk.impl.Validations.Valid
import akka.platform.javasdk.impl.Validations.Validation
import akka.platform.javasdk.impl.action.ActionService
import akka.platform.javasdk.impl.action.ActionsImpl
import akka.platform.javasdk.impl.client.ComponentClientImpl
import akka.platform.javasdk.impl.eventsourcedentity.EventSourcedEntitiesImpl
import akka.platform.javasdk.impl.eventsourcedentity.EventSourcedEntityService
import akka.platform.javasdk.impl.http.HttpClientProviderExtension
import akka.platform.javasdk.impl.http.HttpEndpointOpenRouter
import akka.platform.javasdk.impl.keyvalueentity.KeyValueEntitiesImpl
import akka.platform.javasdk.impl.keyvalueentity.KeyValueEntityService
import akka.platform.javasdk.impl.reflection.Reflect
import akka.platform.javasdk.impl.timer.TimerSchedulerImpl
import akka.platform.javasdk.impl.view.ViewService
import akka.platform.javasdk.impl.view.ViewsImpl
import akka.platform.javasdk.impl.workflow.WorkflowImpl
import akka.platform.javasdk.impl.workflow.WorkflowService
import akka.platform.javasdk.keyvalueentity.KeyValueEntity
import akka.platform.javasdk.keyvalueentity.KeyValueEntityContext
import akka.platform.javasdk.keyvalueentity.KeyValueEntityProvider
import akka.platform.javasdk.keyvalueentity.ReflectiveKeyValueEntityProvider
import akka.platform.javasdk.spi.ComponentClients
import akka.platform.javasdk.spi.EndpointDescriptor
import akka.platform.javasdk.spi.HttpEndpointDescriptor
import akka.platform.javasdk.spi.SpiComponents
import akka.platform.javasdk.timer.TimerScheduler
import akka.platform.javasdk.view.ReflectiveMultiTableViewProvider
import akka.platform.javasdk.view.ReflectiveViewProvider
import akka.platform.javasdk.view.View
import akka.platform.javasdk.view.ViewCreationContext
import akka.platform.javasdk.view.ViewProvider
import akka.platform.javasdk.workflow.AbstractWorkflow
import akka.platform.javasdk.workflow.ReflectiveWorkflowProvider
import akka.platform.javasdk.workflow.Workflow
import akka.platform.javasdk.workflow.WorkflowContext
import akka.platform.javasdk.workflow.WorkflowProvider
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import io.opentelemetry.api.trace.Tracer
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
final case class KalixJavaSdkSettings(
    snapshotEvery: Int,
    cleanupDeletedEventSourcedEntityAfter: Duration,
    cleanupDeletedValueEntityAfter: Duration) {
  def this(config: Config) = {
    this(
      snapshotEvery = config.getInt("event-sourced-entity.snapshot-every"),
      cleanupDeletedEventSourcedEntityAfter = config.getDuration("event-sourced-entity.cleanup-deleted-after"),
      cleanupDeletedValueEntityAfter = config.getDuration("value-entity.cleanup-deleted-after"))
  }
}

/**
 * INTERNAL API
 */
@InternalApi
final class NextGenComponentAutoDetectRunner extends akka.platform.javasdk.spi.Runner {

  override def start(system: ActorSystem[_], runtimeComponentClients: ComponentClients): Future[SpiComponents] = {
    try {

      val app = new NextGenKalixJavaApplication(system, runtimeComponentClients)
      Future.successful(app.spiEndpoints)
    } catch {
      case NonFatal(ex) =>
        println("Unexpected exception while setting up service")
        ex.printStackTrace()
        NextGenKalixJavaApplication.onNextStartCallback.getAndSet(null) match {
          case null =>
          case f    =>
            // Integration test mode
            f.failure(ex)
        }
        throw ex
    }
  }
}

private object ComponentLocator {

  // populated by annotation processor
  private val ComponentDescriptorResourcePath = "META-INF/akka-platform-components.conf"
  private val DescriptorComponentBasePath = s"akka.platform.jvm.sdk.components"
  private val DescriptorServiceEntryPath = s"akka.platform.jvm.sdk.kalix-service"

  private val logger = LoggerFactory.getLogger(getClass)

  case class LocatedClasses(components: Seq[Class[_]], service: Option[Class[_]])

  def locateUserComponents(system: ActorSystem[_]): LocatedClasses = {
    val kalixComponentTypeAndBaseClasses: Map[String, Class[_]] =
      Map(
        "endpoint" -> classOf[AnyRef],
        "action" -> classOf[Action],
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

    if (descriptorConfig.hasPath(DescriptorServiceEntryPath)) {
      // central config/lifecycle class
      val serviceClassName = descriptorConfig.getString(DescriptorServiceEntryPath)
      val serviceClass = system.dynamicAccess.getClassFor[AnyRef](serviceClassName).get
      logger.debug("Found and loaded service class: [{}]", serviceClass)
      LocatedClasses(components, Some(serviceClass))
    } else {
      LocatedClasses(components, None)
    }
  }
}

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] final object NextGenKalixJavaApplication {
  val onNextStartCallback: AtomicReference[Promise[(Kalix, ComponentClients)]] = new AtomicReference(null)
}

private final class NextGenKalixJavaApplication(system: ActorSystem[_], runtimeComponentClients: ComponentClients) {
  private val logger = LoggerFactory.getLogger(getClass)
  private val messageCodec = new JsonMessageCodec
  private val ComponentLocator.LocatedClasses(componentClasses, maybeServiceClass) =
    ComponentLocator.locateUserComponents(system)

  private val endpointTimeout: FiniteDuration =
    system.settings.config
      .getDuration("akka.http.server.request-timeout")
      .toScala
      .plus(10.seconds) // 10s higher than configured timeout, so configured timeout always win

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

  // register them if all valid
  val aclDescriptor =
    maybeServiceClass
      .map(serviceClass => AclDescriptorFactory.buildAclFileDescriptor(serviceClass))
      .getOrElse(AclDescriptorFactory.defaultAclFileDescriptor)

  // FIXME why is acl descriptor needed both here and in discovery?
  // FIXME drop the Kalix class and pull all logic in here
  private val kalix = new Kalix().withDefaultAclFileDescriptor(aclDescriptor)
  componentClasses
    .foreach { clz =>
      if (classOf[Action].isAssignableFrom(clz)) {
        logger.info(s"Registering Action provider for [${clz.getName}]")
        val action = actionProvider(clz.asInstanceOf[Class[Action]])
        kalix.register(action)
      }

      if (classOf[EventSourcedEntity[_, _]].isAssignableFrom(clz)) {
        logger.info(s"Registering EventSourcedEntity provider for [${clz.getName}]")
        val esEntity = eventSourcedEntityProvider(clz.asInstanceOf[Class[EventSourcedEntity[Nothing, Nothing]]])
        kalix.register(esEntity)
      }

      if (classOf[Workflow[_]].isAssignableFrom(clz)) {
        logger.info(s"Registering Workflow provider for [${clz.getName}]")
        val workflow = workflowProvider(clz.asInstanceOf[Class[Workflow[Nothing]]])
        kalix.register(workflow)
      }

      if (classOf[KeyValueEntity[_]].isAssignableFrom(clz)) {
        logger.info(s"Registering KeyValueEntity provider for [${clz.getName}]")
        val valueEntity = valueEntityProvider(clz.asInstanceOf[Class[KeyValueEntity[Nothing]]])
        kalix.register(valueEntity)
      }

      if (classOf[View[_]].isAssignableFrom(clz) && !Reflect.isNestedViewTable(clz)) {
        logger.info(s"Registering View provider for [${clz.getName}]")
        val view = viewProvider(clz.asInstanceOf[Class[View[Nothing]]])
        kalix.register(view)
      }

      if (Reflect.isMultiTableView(clz)) {
        logger.info(s"Registering multi-table View provider for [${clz.getName}]")
        val view = multiTableViewProvider(clz)
        kalix.register(view)
      }
    }

  // collect all Endpoints and compose them to build a larger router
  private val endpointsRouter = componentClasses
    .filter(Reflect.isRestEndpoint)
    .foldLeft(HttpEndpointOpenRouter.empty) { case (router, cls) =>
      router ++ HttpEndpointOpenRouter(
        cls,
        () =>
          wiredInstance(cls) {
            case p if p == classOf[ComponentClient]    => componentClient()
            case h if h == classOf[HttpClientProvider] => httpClientProvider()
          })
    }
    .seal

  // FIXME mixing runtime config with sdk with user project config is tricky
  def spiEndpoints: SpiComponents = {

    var actionsEndpoint: Option[Actions] = None
    var eventSourcedEntitiesEndpoint: Option[EventSourcedEntities] = None
    var valueEntitiesEndpoint: Option[ValueEntities] = None
    var viewsEndpoint: Option[Views] = None
    var workflowEntitiesEndpoint: Option[WorkflowEntities] = None

    val classicSystem = system.classicSystem
    // FIXME pick the right config up without port?
    val configuration =
      new KalixJavaSdkSettings(system.settings.config.getConfig("akka.platform"))

    val serviceFactories = kalix.internalGetServices().asScala
    val services = serviceFactories.toSeq.map { case (serviceName, factory) =>
      serviceName -> factory(system.classicSystem)
    }.toMap
    services.groupBy(_._2.getClass).foreach {

      case (serviceClass, eventSourcedServices: Map[String, EventSourcedEntityService] @unchecked)
          if serviceClass == classOf[EventSourcedEntityService] =>
        val eventSourcedImpl = new EventSourcedEntitiesImpl(classicSystem, eventSourcedServices, configuration)
        eventSourcedEntitiesEndpoint = Some(eventSourcedImpl)

      case (serviceClass, entityServices: Map[String, KeyValueEntityService] @unchecked)
          if serviceClass == classOf[KeyValueEntityService] =>
        valueEntitiesEndpoint = Some(new KeyValueEntitiesImpl(classicSystem, entityServices, configuration))

      case (serviceClass, workflowServices: Map[String, WorkflowService] @unchecked)
          if serviceClass == classOf[WorkflowService] =>
        workflowEntitiesEndpoint =
          Some(new WorkflowImpl(classicSystem, workflowServices, runtimeComponentClients.timerClient))

      case (serviceClass, actionServices: Map[String, ActionService] @unchecked)
          if serviceClass == classOf[ActionService] =>
        actionsEndpoint = Some(new ActionsImpl(classicSystem, actionServices, runtimeComponentClients.timerClient))

      case (serviceClass, viewServices: Map[String, ViewService] @unchecked) if serviceClass == classOf[ViewService] =>
        viewsEndpoint = Some(new ViewsImpl(classicSystem, viewServices))

      case (serviceClass, _) =>
        sys.error(s"Unknown service type: $serviceClass")
    }

    val lifecycleHooks: Option[ServiceLifecycle] = maybeServiceClass match {
      case Some(serviceClass) if classOf[ServiceLifecycle].isAssignableFrom(serviceClass) =>
        // FIXME: HttpClient is tricky because auth headers waiting for discovery, we could maybe sort that
        //        by passing more runtime metadata as parameters to the start call?
        Some(wiredInstance[ServiceLifecycle](serviceClass.asInstanceOf[Class[ServiceLifecycle]]) {
          case p if p == classOf[ComponentClient] => componentClient()
          case t if t == classOf[TimerScheduler] =>
            new TimerSchedulerImpl(
              messageCodec,
              system.classicSystem,
              runtimeComponentClients.timerClient,
              MetadataImpl.Empty)
        })
      case _ => None
    }
    val discoveryEndpoint = new DiscoveryImpl(classicSystem, services, aclDescriptor, BuildInfo.name)

    new SpiComponents {
      override def preStart(system: ActorSystem[_]): Future[Done] = {
        // FIXME this might turn out to not be needed
        Future.successful(Done)
      }

      override def onStart(system: ActorSystem[_]): Future[Done] = {
        // For integration test runner
        NextGenKalixJavaApplication.onNextStartCallback.getAndSet(null) match {
          case null =>
          case f    =>
            // Running inside the test runner
            f.success((kalix, runtimeComponentClients))
        }
        lifecycleHooks match {
          case None => Future.successful(Done)
          case Some(hooks) =>
            hooks.onStartup()
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

      override def endpoints: PartialFunction[HttpRequest, Future[HttpResponse]] =
        endpointsRouter.route(endpointTimeout)(system.classicSystem)

      override val endpointDescriptors: Seq[EndpointDescriptor] =
        endpointsRouter.getTree.allFullPaths.map { path =>
          HttpEndpointDescriptor(HttpMethods.GET, path)
        } ++
        endpointsRouter.postTree.allFullPaths.map { path =>
          HttpEndpointDescriptor(HttpMethods.POST, path)
        } ++
        endpointsRouter.putTree.allFullPaths.map { path =>
          HttpEndpointDescriptor(HttpMethods.PUT, path)
        } ++
        endpointsRouter.patchTree.allFullPaths.map { path =>
          HttpEndpointDescriptor(HttpMethods.PATCH, path)
        } ++
        endpointsRouter.deleteTree.allFullPaths.map { path =>
          HttpEndpointDescriptor(HttpMethods.DELETE, path)
        }
    }
  }

  private def actionProvider[A <: Action](clz: Class[A]): ActionProvider[A] =
    ReflectiveActionProvider.of(
      clz,
      messageCodec,
      context =>
        wiredInstance(clz) {
          case p if p == classOf[ActionCreationContext] => context
          case p if p == classOf[ComponentClient]       => componentClient()
          case h if h == classOf[HttpClientProvider]    => httpClientProvider()
          case p if p == classOf[Tracer]                => context.getTracer
        })

  private def workflowProvider[S, W <: Workflow[S]](clz: Class[W]): WorkflowProvider[S, W] = {
    ReflectiveWorkflowProvider.of(
      clz,
      messageCodec,
      context => {

        val workflow =
          wiredInstance(clz) {
            case p if p == classOf[WorkflowContext]    => context
            case p if p == classOf[ComponentClient]    => componentClient()
            case h if h == classOf[HttpClientProvider] => httpClientProvider()
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
            case asyncCallStep: AbstractWorkflow.AsyncCallStep[_, _, _] =>
              List(asyncCallStep.callInputClass, asyncCallStep.transitionInputClass)
            case callStep: AbstractWorkflow.CallStep[_, _, _, _] =>
              List(callStep.callInputClass, callStep.transitionInputClass)
          }
          .foreach(messageCodec.registerTypeHints)

        workflow
      })
  }

  private def eventSourcedEntityProvider[S, E, ES <: EventSourcedEntity[S, E]](
      clz: Class[ES]): EventSourcedEntityProvider[S, E, ES] =
    ReflectiveEventSourcedEntityProvider.of(
      clz,
      messageCodec,
      context =>
        wiredInstance(clz) {
          case p if p == classOf[EventSourcedEntityContext] => context
        })

  private def valueEntityProvider[S, VE <: KeyValueEntity[S]](clz: Class[VE]): KeyValueEntityProvider[S, VE] =
    ReflectiveKeyValueEntityProvider.of(
      clz,
      messageCodec,
      context =>
        wiredInstance(clz) {
          case p if p == classOf[KeyValueEntityContext] => context
        })

  private def viewProvider[S, V <: View[S]](clz: Class[V]): ViewProvider =
    ReflectiveViewProvider.of[S, V](
      clz,
      messageCodec,
      context =>
        wiredInstance(clz) {
          case p if p == classOf[ViewCreationContext] => context
        })

  private def multiTableViewProvider[V](clz: Class[V]): ViewProvider =
    ReflectiveMultiTableViewProvider.of[V](
      clz,
      messageCodec,
      (viewTableClass, context) => {
        val constructor = viewTableClass.getConstructors.head.asInstanceOf[Constructor[View[_]]]
        wiredInstance(constructor) {
          case p if p == classOf[ViewCreationContext] => context
        }
      })

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
          // FIXME should it be a sub-config for the service rather?
          system.settings.config
        // block wiring of clients into anything that is not an Action or Workflow
        // NOTE: if they are allowed, 'partial' should already have a matching case for them
        // if partial func doesn't match, try to lookup in the applicationContext
        case anyOther =>
          throw new RuntimeException(
            s"[${constructor.getDeclaringClass.getName}] are not allowed to have a dependency on ${anyOther.getName}");

      }

    // all params must be wired so we use 'map' not 'collect'
    val params = constructor.getParameterTypes.map(totalWireFunction)

    constructor.newInstance(params: _*)
  }

  private def componentClient(): ComponentClient = {
    ComponentClientImpl(runtimeComponentClients)(system.executionContext)
  }

  private def httpClientProvider(): HttpClientProvider =
    HttpClientProviderExtension(system)

}
