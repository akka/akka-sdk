/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package kalix.javasdk.impl

import java.lang.reflect.Constructor
import java.lang.reflect.ParameterizedType
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference

import scala.concurrent.Future
import scala.concurrent.Promise
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.jdk.CollectionConverters.MapHasAsScala
import scala.reflect.ClassTag
import scala.util.control.NonFatal

import akka.Done
import akka.actor.typed.ActorSystem
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import io.opentelemetry.api.trace.Tracer
import kalix.javasdk.Context
import kalix.javasdk.Kalix
import kalix.javasdk.ServiceLifecycle
import kalix.javasdk.action.Action
import kalix.javasdk.action.ActionCreationContext
import kalix.javasdk.action.ActionProvider
import kalix.javasdk.action.ReflectiveActionProvider
import kalix.javasdk.client.ComponentClient
import kalix.javasdk.eventsourced.ReflectiveEventSourcedEntityProvider
import kalix.javasdk.eventsourcedentity.EventSourcedEntity
import kalix.javasdk.eventsourcedentity.EventSourcedEntityContext
import kalix.javasdk.eventsourcedentity.EventSourcedEntityProvider
import kalix.javasdk.impl.Validations.Invalid
import kalix.javasdk.impl.Validations.Valid
import kalix.javasdk.impl.Validations.Validation
import kalix.javasdk.impl.action.ActionService
import kalix.javasdk.impl.action.ActionsImpl
import kalix.javasdk.impl.client.ComponentClientImpl
import kalix.javasdk.impl.eventsourcedentity.EventSourcedEntitiesImpl
import kalix.javasdk.impl.eventsourcedentity.EventSourcedEntityService
import kalix.javasdk.impl.reflection.Reflect
import kalix.javasdk.impl.valueentity.ValueEntitiesImpl
import kalix.javasdk.impl.valueentity.ValueEntityService
import kalix.javasdk.impl.view.ViewService
import kalix.javasdk.impl.view.ViewsImpl
import kalix.javasdk.impl.workflow.WorkflowImpl
import kalix.javasdk.impl.workflow.WorkflowService
import kalix.javasdk.spi.SpiEndpoints
import kalix.javasdk.valueentity.ReflectiveValueEntityProvider
import kalix.javasdk.valueentity.ValueEntity
import kalix.javasdk.valueentity.ValueEntityContext
import kalix.javasdk.valueentity.ValueEntityProvider
import kalix.javasdk.view.ReflectiveMultiTableViewProvider
import kalix.javasdk.view.ReflectiveViewProvider
import kalix.javasdk.view.View
import kalix.javasdk.view.ViewCreationContext
import kalix.javasdk.view.ViewProvider
import kalix.javasdk.workflow.AbstractWorkflow
import kalix.javasdk.workflow.ReflectiveWorkflowProvider
import kalix.javasdk.workflow.Workflow
import kalix.javasdk.workflow.WorkflowContext
import kalix.javasdk.workflow.WorkflowProvider
import kalix.protocol.action.Actions
import kalix.protocol.discovery.Discovery
import kalix.protocol.event_sourced_entity.EventSourcedEntities
import kalix.protocol.replicated_entity.ReplicatedEntities
import kalix.protocol.value_entity.ValueEntities
import kalix.protocol.view.Views
import kalix.protocol.workflow_entity.WorkflowEntities
import kalix.spring.BuildInfo
import kalix.spring.WebClientProvider
import kalix.spring.impl.KalixClient
import kalix.spring.impl.RestKalixClientImpl
import kalix.spring.impl.WebClientProviderHolder
import org.slf4j.LoggerFactory

final case class KalixJavaSdkSettings(
    userFunctionInterface: String,
    userFunctionPort: Int,
    snapshotEvery: Int,
    cleanupDeletedEventSourcedEntityAfter: Duration,
    cleanupDeletedValueEntityAfter: Duration) {
  validate()
  def this(config: Config) = {
    this(
      userFunctionInterface = config.getString("user-function-interface"),
      userFunctionPort = config.getInt("user-function-port"),
      snapshotEvery = config.getInt("event-sourced-entity.snapshot-every"),
      cleanupDeletedEventSourcedEntityAfter = config.getDuration("event-sourced-entity.cleanup-deleted-after"),
      cleanupDeletedValueEntityAfter = config.getDuration("value-entity.cleanup-deleted-after"))
  }

  private def validate(): Unit = {
    require(userFunctionInterface.nonEmpty, s"user-function-interface must not be empty")
    require(userFunctionPort > 0, s"user-function-port must be greater than 0")
  }
}

final class NextGenComponentAutoDetectRunner extends kalix.javasdk.spi.Runner {

  override def start(system: ActorSystem[_]): Future[SpiEndpoints] = {
    try {
      val app = new NextGenKalixJavaApplication(system)
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
  private val ComponentDescriptorResourcePath = "META-INF/kalix-components.conf"
  private val DescriptorComponentBasePath = s"kalix.jvm.sdk.components"
  private val DescriptorServiceEntryPath = s"kalix.jvm.sdk.kalix-service"

  private val logger = LoggerFactory.getLogger(getClass)

  case class LocatedClasses(components: Seq[Class[_]], service: Option[Class[_]])

  def locateUserComponents(system: ActorSystem[_]): LocatedClasses = {
    val kalixComponentTypeAndBaseClasses: Map[String, Class[_]] =
      Map(
        "action" -> classOf[Action],
        "event-sourced-entity" -> classOf[EventSourcedEntity[_, _]],
        "workflow" -> classOf[Workflow[_]],
        "value-entity" -> classOf[ValueEntity[_]],
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

private final object NextGenKalixJavaApplication {
  val onNextStartCallback: AtomicReference[Promise[(Kalix, KalixClient)]] = new AtomicReference(null)
}

private final class NextGenKalixJavaApplication(system: ActorSystem[_]) {
  private val logger = LoggerFactory.getLogger(getClass)
  private val messageCodec = new JsonMessageCodec
  private val kalixClient = new RestKalixClientImpl(messageCodec)
  private val ComponentLocator.LocatedClasses(componentClasses, maybeServiceClass) =
    ComponentLocator.locateUserComponents(system)

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
  private val kalix = new Kalix().withDefaultAclFileDescriptor(aclDescriptor)
  componentClasses
    .foreach { clz =>
      if (classOf[Action].isAssignableFrom(clz)) {
        logger.info(s"Registering Action provider for [${clz.getName}]")
        val action = actionProvider(clz.asInstanceOf[Class[Action]])
        kalix.register(action)
        kalixClient.registerComponent(action.serviceDescriptor())
      }

      if (classOf[EventSourcedEntity[_, _]].isAssignableFrom(clz)) {
        logger.info(s"Registering EventSourcedEntity provider for [${clz.getName}]")
        val esEntity = eventSourcedEntityProvider(clz.asInstanceOf[Class[EventSourcedEntity[Nothing, Nothing]]])
        kalix.register(esEntity)
        kalixClient.registerComponent(esEntity.serviceDescriptor())
      }

      if (classOf[Workflow[_]].isAssignableFrom(clz)) {
        logger.info(s"Registering Workflow provider for [${clz.getName}]")
        val workflow = workflowProvider(clz.asInstanceOf[Class[Workflow[Nothing]]])
        kalix.register(workflow)
        kalixClient.registerComponent(workflow.serviceDescriptor())
      }

      if (classOf[ValueEntity[_]].isAssignableFrom(clz)) {
        logger.info(s"Registering ValueEntity provider for [${clz.getName}]")
        val valueEntity = valueEntityProvider(clz.asInstanceOf[Class[ValueEntity[Nothing]]])
        kalix.register(valueEntity)
        kalixClient.registerComponent(valueEntity.serviceDescriptor())
      }

      if (classOf[View[_]].isAssignableFrom(clz) && !Reflect.isNestedViewTable(clz)) {
        logger.info(s"Registering View provider for [${clz.getName}]")
        val view = viewProvider(clz.asInstanceOf[Class[View[Nothing]]])
        kalix.register(view)
        kalixClient.registerComponent(view.serviceDescriptor())
      }

      if (Reflect.isMultiTableView(clz)) {
        logger.info(s"Registering multi-table View provider for [${clz.getName}]")
        val view = multiTableViewProvider(clz)
        kalix.register(view)
        kalixClient.registerComponent(view.serviceDescriptor())
      }
    }

  // FIXME mixing runtime config with sdk with user project config is tricky
  def spiEndpoints: SpiEndpoints = {

    var actionsEndpoint: Option[Actions] = None
    var eventSourcedEntitiesEndpoint: Option[EventSourcedEntities] = None
    var valueEntitiesEndpoint: Option[ValueEntities] = None
    var viewsEndpoint: Option[Views] = None
    var workflowEntitiesEndpoint: Option[WorkflowEntities] = None

    val classicSystem = system.classicSystem
    // FIXME pick the right config up without port?
    val configuration =
      new KalixJavaSdkSettings(system.settings.config.getConfig("kalix"))

    val serviceFactories = kalix.internalGetServices().asScala
    val services = serviceFactories.toSeq.map { case (serviceName, factory) =>
      serviceName -> factory(system.classicSystem)
    }.toMap
    services.groupBy(_._2.getClass).foreach {

      case (serviceClass, eventSourcedServices: Map[String, EventSourcedEntityService] @unchecked)
          if serviceClass == classOf[EventSourcedEntityService] =>
        val eventSourcedImpl = new EventSourcedEntitiesImpl(classicSystem, eventSourcedServices, configuration)
        eventSourcedEntitiesEndpoint = Some(eventSourcedImpl)

      case (serviceClass, entityServices: Map[String, ValueEntityService] @unchecked)
          if serviceClass == classOf[ValueEntityService] =>
        valueEntitiesEndpoint = Some(new ValueEntitiesImpl(classicSystem, entityServices, configuration))

      case (serviceClass, workflowServices: Map[String, WorkflowService] @unchecked)
          if serviceClass == classOf[WorkflowService] =>
        workflowEntitiesEndpoint = Some(new WorkflowImpl(classicSystem, workflowServices))

      case (serviceClass, actionServices: Map[String, ActionService] @unchecked)
          if serviceClass == classOf[ActionService] =>
        actionsEndpoint = Some(new ActionsImpl(classicSystem, actionServices))

      case (serviceClass, viewServices: Map[String, ViewService] @unchecked) if serviceClass == classOf[ViewService] =>
        viewsEndpoint = Some(new ViewsImpl(classicSystem, viewServices))

      case (serviceClass, _) =>
        sys.error(s"Unknown service type: $serviceClass")
    }

    val lifecycleHooks: Option[ServiceLifecycle] = maybeServiceClass match {
      case Some(serviceClass) if classOf[ServiceLifecycle].isAssignableFrom(serviceClass) =>
        Some(
          system.dynamicAccess
            .createInstanceFor[ServiceLifecycle](serviceClass, Seq.empty)
            .getOrElse(throw new RuntimeException(
              s"Service lifecycle class [$serviceClass] does not have a zero argument public constructor")))
      case _ => None
    }
    val discoveryEndpoint = new DiscoveryImpl(classicSystem, services, aclDescriptor, BuildInfo.name)

    new SpiEndpoints {
      override def preStart(system: ActorSystem[_]): Future[Done] = {
        // FIXME hook up docker compose starting for dev mode here
        Future.successful(Done)
      }

      override def onStart(system: ActorSystem[_]): Future[Done] = {
        // For integration test runner
        NextGenKalixJavaApplication.onNextStartCallback.getAndSet(null) match {
          case null =>
          case f    =>
            // Running inside the test runner
            f.success((kalix, kalixClient))
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
    }
  }

  private def actionProvider[A <: Action](clz: Class[A]): ActionProvider[A] =
    ReflectiveActionProvider.of(
      clz,
      messageCodec,
      context =>
        wiredInstance(clz) {
          case p if p == classOf[ActionCreationContext] => context
          case p if p == classOf[ComponentClient]       => componentClient(context)
          case p if p == classOf[WebClientProvider]     => webClientProvider(context)
          case p if p == classOf[Tracer]                => context.getTracer
        })

  private def workflowProvider[S, W <: Workflow[S]](clz: Class[W]): WorkflowProvider[S, W] = {
    ReflectiveWorkflowProvider.of(
      clz,
      messageCodec,
      context => {

        val workflow =
          wiredInstance(clz) {
            case p if p == classOf[WorkflowContext]   => context
            case p if p == classOf[ComponentClient]   => componentClient(context)
            case p if p == classOf[WebClientProvider] => webClientProvider(context)
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

  private def valueEntityProvider[S, VE <: ValueEntity[S]](clz: Class[VE]): ValueEntityProvider[S, VE] =
    ReflectiveValueEntityProvider.of(
      clz,
      messageCodec,
      context =>
        wiredInstance(clz) {
          case p if p == classOf[ValueEntityContext] => context
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

  private def webClientProvider(context: Context) = {
    val webClientProviderHolder = WebClientProviderHolder(context.materializer().system)
    webClientProviderHolder.webClientProvider
  }

  private def componentClient(context: Context): ComponentClient = {
    kalixClient.setWebClient(webClientProvider(context).localWebClient)
    // Important!
    // always new ComponentClient instance because we need to set the call context each time
    // and we should not share state between call
    new ComponentClientImpl(kalixClient)
  }
}
