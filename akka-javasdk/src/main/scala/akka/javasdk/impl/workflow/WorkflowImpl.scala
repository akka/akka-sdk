/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.workflow

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.jdk.CollectionConverters.ListHasAsScala
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
import akka.javasdk.impl.MetadataImpl
import akka.javasdk.impl.Service
import akka.javasdk.impl.WorkflowExceptions.WorkflowException
import akka.javasdk.impl.serialization.JsonSerializer
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
import akka.javasdk.impl.workflow.WorkflowImpl.NoCommandPayload
import akka.javasdk.impl.workflow.WorkflowRouter.CommandResult
import akka.javasdk.workflow.CommandContext
import akka.javasdk.workflow.Workflow
import akka.javasdk.workflow.Workflow.{ RecoverStrategy => SdkRecoverStrategy }
import akka.javasdk.workflow.WorkflowContext
import akka.runtime.sdk.spi.BytesPayload
import akka.runtime.sdk.spi.SpiEntity
import akka.runtime.sdk.spi.SpiMetadata
import akka.runtime.sdk.spi.SpiWorkflow
import akka.runtime.sdk.spi.TimerClient
import akka.util.ByteString
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import kalix.protocol.workflow_entity.WorkflowEntities

/**
 * INTERNAL API
 */
@InternalApi
private[impl] object WorkflowImpl {
  private val NoCommandPayload = new BytesPayload(ByteString.empty, AnySupport.JsonTypeUrlPrefix)
}

/**
 * INTERNAL API
 */
@InternalApi
class WorkflowImpl[S, W <: Workflow[S]](
    workflowId: String,
    componentClass: Class[_],
    serializer: JsonSerializer,
    timerClient: TimerClient,
    sdkExecutionContext: ExecutionContext,
    tracerFactory: () => Tracer,
    instanceFactory: Function[WorkflowContext, W])
    extends SpiWorkflow {

  private val componentDescriptor = ComponentDescriptor.descriptorFor(componentClass, serializer)
  private val context = new WorkflowContextImpl(workflowId)

  private val router =
    new ReflectiveWorkflowRouter[S, W](instanceFactory(context), componentDescriptor.commandHandlers, serializer)

  override def configuration: SpiWorkflow.WorkflowConfig = {
    val definition = router.workflow.definition()

    def toRecovery(sdkRecoverStrategy: SdkRecoverStrategy[_]): SpiWorkflow.RecoverStrategy = {

      val stepTransition = new SpiWorkflow.StepTransition(
        sdkRecoverStrategy.failoverStepName,
        sdkRecoverStrategy.failoverStepInput.toScala.map(serializer.toBytes))
      new SpiWorkflow.RecoverStrategy(sdkRecoverStrategy.maxRetries, failoverTo = stepTransition)
    }

    val failoverTo = {
      definition.getFailoverStepName.toScala.map { stepName =>
        new SpiWorkflow.StepTransition(stepName, definition.getFailoverStepInput.toScala.map(serializer.toBytes))
      }
    }

    val stepConfigs =
      definition.getStepConfigs.asScala.map { config =>
        val stepTimeout = config.timeout.toScala.map(_.toScala)
        val failoverRecoverStrategy = config.recoverStrategy.toScala.map(toRecovery)
        (config.stepName, new SpiWorkflow.StepConfig(config.stepName, stepTimeout, failoverRecoverStrategy))
      }.toMap

    val failoverRecoverStrategy = definition.getStepRecoverStrategy.toScala.map(toRecovery)
    val stepTimeout = definition.getStepTimeout.toScala.map(_.toScala)

    val defaultStepConfig = Option.when(failoverRecoverStrategy.isDefined) {
      new SpiWorkflow.StepConfig("", stepTimeout, failoverRecoverStrategy)
    }

    new SpiWorkflow.WorkflowConfig(
      workflowTimeout = definition.getWorkflowTimeout.toScala.map(_.toScala),
      failoverTo = failoverTo,
      failoverRecoverStrategy = failoverRecoverStrategy,
      defaultStepTimeout = stepTimeout,
      defaultStepConfig = defaultStepConfig,
      stepConfigs = stepConfigs)
  }

  override def emptyState(workflowId: String): SpiWorkflow.State =
    if (router.workflow.emptyState() == null) BytesPayload.empty
    else serializer.toBytes(router.workflow.emptyState())

  private def commandContext(commandName: String, metadata: Metadata = MetadataImpl.Empty) =
    new CommandContextImpl(
      workflowId,
      commandName,
      metadata,
      // FIXME we'd need to start a parent span for the command here to have one to base custom user spans of off?
      None,
      tracerFactory)

  private def decodeState(userState: Option[BytesPayload]): S =
    userState
      .map(serializer.fromBytes)
      .getOrElse(router.workflow.emptyState())
      .asInstanceOf[S]

  private def toSpiEffect(effect: Workflow.Effect[_]): SpiWorkflow.Effect = {

    def toSpiTransition(transition: Transition): SpiWorkflow.Transition =
      transition match {
        case StepTransition(stepName, input) =>
          new SpiWorkflow.StepTransition(stepName, input.map(serializer.toBytes))
        case Pause        => SpiWorkflow.Pause
        case NoTransition => SpiWorkflow.NoTransition
        case End          => SpiWorkflow.End
      }

    def handleState(persistence: Persistence[Any], transition: Transition): SpiWorkflow.Persistence =
      persistence match {
        case UpdateState(newState) =>
          router._internalSetInitState(newState, transition.isInstanceOf[End.type])
          new SpiWorkflow.UpdateState(serializer.toBytes(newState))
        case DeleteState   => SpiWorkflow.DeleteState
        case NoPersistence => SpiWorkflow.NoPersistence
      }

    effect match {
      case error: ErrorEffectImpl[_] =>
        new SpiWorkflow.Effect(
          userState = SpiWorkflow.NoPersistence, // mean runtime don't need to persist any new state
          SpiWorkflow.NoTransition,
          reply = None,
          error = Some(new SpiEntity.Error(error.description)),
          metadata = SpiMetadata.Empty)

      case WorkflowEffectImpl(persistence, transition, reply) =>
        val (replyOpt, spiMetadata) =
          reply match {
            case ReplyValue(value, metadata) => (Some(value), MetadataImpl.toSpi(metadata))
            // discarded
            case NoReply => (None, SpiMetadata.Empty)
          }

        new SpiWorkflow.Effect(
          handleState(persistence, transition), // can be null when fallback to emptyState
          toSpiTransition(transition),
          reply = replyOpt.map(serializer.toBytes),
          error = None,
          metadata = spiMetadata)

      case TransitionalEffectImpl(persistence, transition) =>
        new SpiWorkflow.Effect(
          handleState(persistence, transition), // can be null when fallback to emptyState
          toSpiTransition(transition),
          reply = None,
          error = None,
          metadata = SpiMetadata.Empty)
    }
  }

  override def handleCommand(
      workflowState: Option[SpiWorkflow.WorkflowState],
      command: SpiEntity.Command): Future[SpiWorkflow.Effect] = {

    // can be null when fallback to emptyState
    val decodedState = decodeState(workflowState.map(_.userState))
    val isFinished = workflowState.exists(_.isFinished)
    router._internalSetInitState(decodedState, finished = isFinished)

    val metadata = MetadataImpl.of(command.metadata)
    val context = commandContext(command.name, metadata)

    val timerScheduler =
      new TimerSchedulerImpl(timerClient, context.componentCallMetadata)
    val cmd = command.payload.getOrElse {
      // FIXME smuggling 0 arity method called from component client through here
      NoCommandPayload
    }

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
          throw WorkflowException(workflowId, command.name, s"Unexpected failure: $error", Some(error))
      }

    Future.successful(toSpiEffect(effect))
  }

  override def executeStep(
      stepName: String,
      input: Option[BytesPayload],
      userState: Option[BytesPayload]): Future[BytesPayload] = {

    val context = commandContext(stepName)
    val timerScheduler =
      new TimerSchedulerImpl(timerClient, context.componentCallMetadata)

    try {
      userState.foreach { state =>
        val decoded = serializer.fromBytes(state)
        router._internalSetInitState(decoded, finished = false) // here we know that workflow is still running
      }

      router._internalHandleStep(
        input = input,
        stepName = stepName,
        serializer = serializer,
        timerScheduler = timerScheduler,
        commandContext = context,
        executionContext = sdkExecutionContext)
    } catch {
      case e: WorkflowException => throw e
      case NonFatal(ex) =>
        throw WorkflowException(s"unexpected exception [${ex.getMessage}] while executing step [${stepName}]", Some(ex))
    }
  }

  override def transition(stepName: String, result: Option[BytesPayload]): Future[SpiWorkflow.Effect] = {
    val CommandResult(effect) =
      try {
        router._internalGetNextStep(stepName, result.get, serializer)
      } catch {
        case e: WorkflowException => throw e
        case NonFatal(ex) =>
          throw WorkflowException(
            s"unexpected exception [${ex.getMessage}] while executing transition for step [${stepName}]",
            Some(ex))
      }
    Future.successful(toSpiEffect(effect))
  }

}

/**
 * INTERNAL API
 */
@InternalApi
final class WorkflowService[S, W <: Workflow[S]](
    workflowClass: Class[_],
    serializer: JsonSerializer,
    instanceFactory: Function[WorkflowContext, W])
    extends Service(workflowClass, WorkflowEntities.name, serializer) {

  def createRouter(context: WorkflowContext) =
    new ReflectiveWorkflowRouter[S, W](instanceFactory(context), componentDescriptor.commandHandlers, serializer)

}

/**
 * INTERNAL API
 */
@InternalApi
private[akka] final class CommandContextImpl(
    override val workflowId: String,
    override val commandName: String,
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
