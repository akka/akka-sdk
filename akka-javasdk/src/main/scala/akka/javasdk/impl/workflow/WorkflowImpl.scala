/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.workflow

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.jdk.DurationConverters.JavaDurationOps
import scala.jdk.OptionConverters.RichOptional
import scala.util.control.NonFatal

import akka.annotation.InternalApi
import akka.javasdk.Metadata
import akka.javasdk.Tracing
import akka.javasdk.impl.AbstractContext
import akka.javasdk.impl.ActivatableContext
import akka.javasdk.impl.AnySupport
import akka.javasdk.impl.ComponentDescriptor
import akka.javasdk.impl.ErrorHandling.BadRequestException
import akka.javasdk.impl.JsonMessageCodec
import akka.javasdk.impl.MetadataImpl
import akka.javasdk.impl.Service
import akka.javasdk.impl.StrictJsonMessageCodec
import akka.javasdk.impl.WorkflowExceptions.WorkflowException
import akka.javasdk.impl.telemetry.SpanTracingImpl
import akka.javasdk.impl.timer.TimerSchedulerImpl
import akka.javasdk.impl.workflow.WorkflowEffectImpl.DeleteState
import akka.javasdk.impl.workflow.WorkflowEffectImpl.End
import akka.javasdk.impl.workflow.WorkflowEffectImpl.ErrorEffectImpl
import akka.javasdk.impl.workflow.WorkflowEffectImpl.NoPersistence
import akka.javasdk.impl.workflow.WorkflowEffectImpl.NoReply
import akka.javasdk.impl.workflow.WorkflowEffectImpl.NoTransition
import akka.javasdk.impl.workflow.WorkflowEffectImpl.Pause
import akka.javasdk.impl.workflow.WorkflowEffectImpl.Persistence
import akka.javasdk.impl.workflow.WorkflowEffectImpl.ReplyValue
import akka.javasdk.impl.workflow.WorkflowEffectImpl.StepTransition
import akka.javasdk.impl.workflow.WorkflowEffectImpl.Transition
import akka.javasdk.impl.workflow.WorkflowEffectImpl.TransitionalEffectImpl
import akka.javasdk.impl.workflow.WorkflowEffectImpl.UpdateState
import akka.javasdk.impl.workflow.WorkflowRouter.CommandResult
import akka.javasdk.workflow.CommandContext
import akka.javasdk.workflow.Workflow
import akka.javasdk.workflow.WorkflowContext
import akka.runtime.sdk.spi.SpiEntity
import akka.runtime.sdk.spi.SpiSerialization
import akka.runtime.sdk.spi.SpiSerialization.Deserialized
import akka.runtime.sdk.spi.SpiWorkflow
import akka.runtime.sdk.spi.SpiWorkflow.State
import akka.runtime.sdk.spi.TimerClient
import com.google.protobuf.ByteString
import com.google.protobuf.any
import com.google.protobuf.any.{ Any => PbAny }
import com.google.protobuf.any.{ Any => ScalaPbAny }
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import kalix.protocol.workflow_entity.WorkflowEntities

/**
 * INTERNAL API
 */
@InternalApi
class WorkflowImpl[S, W <: Workflow[S]](
    workflowId: String,
    componentClass: Class[_],
    messageCodec: JsonMessageCodec,
    timerClient: TimerClient,
    sdkExecutionContext: ExecutionContext,
    tracerFactory: () => Tracer,
    instanceFactory: Function[WorkflowContext, W])
    extends SpiWorkflow {

  private val strictMessageCodec = new StrictJsonMessageCodec(messageCodec)
  private val componentDescriptor = ComponentDescriptor.descriptorFor(componentClass, messageCodec)
  private val context = new WorkflowContextImpl(workflowId)

  private val router =
    new ReflectiveWorkflowRouter[S, W](instanceFactory(context), componentDescriptor.commandHandlers)

  override def configuration: SpiWorkflow.WorkflowConfig = {
    val definition = router.workflow.definition()

    // FIXME: map all configs
    new SpiWorkflow.WorkflowConfig(
      workflowTimeout = definition.getWorkflowTimeout.toScala.map(_.toScala),
      failoverTo = None,
      failoverRecoverStrategy = None,
      defaultStepTimeout = definition.getStepTimeout.toScala.map(_.toScala),
      defaultStepConfig = None,
      stepConfigs = Map.empty)
  }

  override def emptyState(workflowId: String): SpiWorkflow.State = {
    router.workflow.emptyState().asInstanceOf[SpiWorkflow.State]
  }

  private def commandContext(commandName: String, metadata: Metadata = MetadataImpl.Empty) =
    new CommandContextImpl(
      workflowId,
      commandName,
      commandId = 0, // FIXME: remove if proved obsolete
      metadata,
      // FIXME we'd need to start a parent span for the command here to have one to base custom user spans of off?
      None,
      tracerFactory)

  private def decodeState(userState: Option[PbAny]) =
    userState
      .map(strictMessageCodec.decodeMessage)
      .getOrElse(router.workflow.emptyState())
      .asInstanceOf[SpiWorkflow.State]

  private def toSpiEffect(effect: Workflow.Effect[_]): SpiWorkflow.Effect = {

    def toSpiTransition(transition: Transition): SpiWorkflow.Transition =
      transition match {
        case StepTransition(stepName, input) =>
          new SpiWorkflow.StepTransition(stepName, input.map(_.asInstanceOf[SpiSerialization.Deserialized]))
        case Pause        => SpiWorkflow.Pause
        case NoTransition => SpiWorkflow.NoTransition
        case End          => SpiWorkflow.End
      }

    def handleState(persistence: Persistence[Any], transition: Transition): SpiWorkflow.Persistence =
      persistence match {
        case UpdateState(newState) =>
          router._internalSetInitState(newState, transition.isInstanceOf[End.type])
          new SpiWorkflow.UpdateState(newState.asInstanceOf[State])
        case DeleteState   => SpiWorkflow.DeleteState
        case NoPersistence => SpiWorkflow.NoPersistence
      }

    effect match {
      case error: ErrorEffectImpl[_] =>
        new SpiWorkflow.Effect(
          userState = SpiWorkflow.NoPersistence, // mean runtime don't need to persist any new state
          SpiWorkflow.NoTransition,
          reply = None,
          error = Some(new SpiEntity.Error(error.description)))

      case WorkflowEffectImpl(persistence, transition, reply) =>
        val replyOpt = reply match {
          case ReplyValue(value, _) => Some(value) // FIXME: Metadata is being discarded
          case NoReply              => None
        }

        new SpiWorkflow.Effect(
          handleState(persistence, transition), // can be null when fallback to emptyState
          toSpiTransition(transition),
          reply = replyOpt.map(_.asInstanceOf[SpiSerialization.Deserialized]),
          error = None)

      case TransitionalEffectImpl(persistence, transition) =>
        new SpiWorkflow.Effect(
          handleState(persistence, transition), // can be null when fallback to emptyState
          toSpiTransition(transition),
          reply = None,
          error = None)
    }
  }

  override def handleCommand(userState: Option[PbAny], command: SpiEntity.Command): Future[SpiWorkflow.Effect] = {

    // can be null when fallback to emptyState
    val decodedState = decodeState(userState)

    // FIXME: how to decide when finished?
    router._internalSetInitState(decodedState, finished = false)

    val metadata = MetadataImpl.Empty // FIXME: build it from Spi Metadata
    //MetadataImpl.of(command.metadata.map(_.entries.toVector).getOrElse(Nil))
    val context = commandContext(command.name, metadata)

    val timerScheduler =
      new TimerSchedulerImpl(strictMessageCodec, timerClient, context.componentCallMetadata)

    val cmd =
      messageCodec.decodeMessage(
        command.payload.getOrElse(
          // FIXME smuggling 0 arity method called from component client through here
          ScalaPbAny.defaultInstance.withTypeUrl(AnySupport.JsonTypeUrlPrefix).withValue(ByteString.empty())))

    val CommandResult(effect) =
      try {
        router._internalHandleCommand(
          commandName = command.name,
          command = cmd,
          context = context,
          timerScheduler = timerScheduler)
      } catch {
        case BadRequestException(msg) => CommandResult(WorkflowEffectImpl[Any]().error(msg))
        case e: WorkflowException     => throw e
        case NonFatal(error) =>
          throw WorkflowException(
            command.entityId,
            commandId = 0, // FIXME: remove if proved obsolete
            command.name,
            s"Unexpected failure: $error",
            Some(error))
      }

    Future.successful(toSpiEffect(effect))
  }

  override def executeStep(stepName: String, input: Option[PbAny], userState: Option[PbAny]): Future[Deserialized] = {

    val context = commandContext(stepName)
    val timerScheduler =
      new TimerSchedulerImpl(strictMessageCodec, timerClient, context.componentCallMetadata)

    try {
      userState.foreach { state =>
        val decoded = strictMessageCodec.decodeMessage(state)
        router._internalSetInitState(decoded, finished = false) // here we know that workflow is still running
      }

      router._internalHandleStep(
        commandId = 0, // FIXME: remove if proved obsolete
        input = input,
        stepName = stepName,
        messageCodec = strictMessageCodec,
        timerScheduler = timerScheduler,
        commandContext = context,
        executionContext = sdkExecutionContext)
    } catch {
      case e: WorkflowException => throw e
      case NonFatal(ex) =>
        throw WorkflowException(s"unexpected exception [${ex.getMessage}] while executing step [${stepName}]", Some(ex))
    }
  }

  override def transition(stepName: String, result: Option[PbAny]): Future[SpiWorkflow.Effect] = {
    // can be null when fallback to emptyState
    val CommandResult(effect) =
      try {
        router._internalGetNextStep(stepName, result.get, strictMessageCodec)
      } catch {
        case e: WorkflowException => throw e
        case NonFatal(ex) =>
          throw WorkflowException(
            s"unexpected exception [${ex.getMessage}] while executing transition for step [${stepName}]",
            Some(ex))
      }
    Future.successful(toSpiEffect(effect))
  }

  private val codec = new SpiSerialization.Serializer {

    override def toProto(obj: Deserialized): any.Any =
      messageCodec.encodeScala(obj)

    override def fromProto[T](pb: PbAny, clz: Class[T]): T = {
      // FIXME: use BytesPayload from Johan's PR
      messageCodec.decodeMessage(clz, akka.util.ByteString.fromArrayUnsafe(pb.value.toByteArray))
    }
  }
  override def serializer: SpiSerialization.Serializer = codec
}

/**
 * INTERNAL API
 */
@InternalApi
final class WorkflowService[S, W <: Workflow[S]](
    workflowClass: Class[_],
    messageCodec: JsonMessageCodec,
    instanceFactory: Function[WorkflowContext, W])
    extends Service(workflowClass, WorkflowEntities.name, messageCodec) {

  def createRouter(context: WorkflowContext) =
    new ReflectiveWorkflowRouter[S, W](instanceFactory(context), componentDescriptor.commandHandlers)

  val strictMessageCodec = new StrictJsonMessageCodec(messageCodec)

}

/**
 * INTERNAL API
 */
@InternalApi
private[akka] final class CommandContextImpl(
    override val workflowId: String,
    override val commandName: String,
    override val commandId: Long,
    override val metadata: Metadata,
    span: Option[Span],
    tracerFactory: () => Tracer)
    extends AbstractContext
    with CommandContext
    with ActivatableContext {

  override def tracing(): Tracing =
    new SpanTracingImpl(span, tracerFactory)
}

/**
 * INTERNAL API
 */
@InternalApi
private[akka] final class WorkflowContextImpl(override val workflowId: String)
    extends AbstractContext
    with WorkflowContext
