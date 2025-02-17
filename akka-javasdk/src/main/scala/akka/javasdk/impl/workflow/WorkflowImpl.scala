/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.workflow

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.jdk.CollectionConverters.ListHasAsScala
import scala.jdk.DurationConverters.JavaDurationOps
import scala.jdk.OptionConverters.RichOptional
import scala.util.Failure
import scala.util.Success
import scala.util.control.NonFatal

import akka.annotation.InternalApi
import akka.javasdk.Metadata
import akka.javasdk.Tracing
import akka.javasdk.impl.AbstractContext
import akka.javasdk.impl.ActivatableContext
import akka.javasdk.impl.ComponentDescriptor
import akka.javasdk.impl.ErrorHandling.BadRequestException
import akka.javasdk.impl.HandlerNotFoundException
import akka.javasdk.impl.MetadataImpl
import akka.javasdk.impl.WorkflowExceptions.WorkflowException
import akka.javasdk.impl.serialization.JsonSerializer
import akka.javasdk.impl.telemetry.SpanTracingImpl
import akka.javasdk.impl.timer.TimerSchedulerImpl
import akka.javasdk.impl.workflow.ReflectiveWorkflowRouter.CommandResult
import akka.javasdk.impl.workflow.ReflectiveWorkflowRouter.TransitionalResult
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
import akka.javasdk.workflow.CommandContext
import akka.javasdk.workflow.Workflow
import akka.javasdk.workflow.Workflow.{ RecoverStrategy => SdkRecoverStrategy }
import akka.javasdk.workflow.WorkflowContext
import akka.runtime.sdk.spi.BytesPayload
import akka.runtime.sdk.spi.RegionInfo
import akka.runtime.sdk.spi.SpiEntity
import akka.runtime.sdk.spi.SpiMetadata
import akka.runtime.sdk.spi.SpiWorkflow
import akka.runtime.sdk.spi.TimerClient
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * INTERNAL API
 */
@InternalApi
class WorkflowImpl[S, W <: Workflow[S]](
    workflowId: String,
    workflowClass: Class[W],
    serializer: JsonSerializer,
    componentDescriptor: ComponentDescriptor,
    timerClient: TimerClient,
    sdkExecutionContext: ExecutionContext,
    tracerFactory: () => Tracer,
    regionInfo: RegionInfo,
    instanceFactory: Function[WorkflowContext, W])
    extends SpiWorkflow {

  private val log: Logger = LoggerFactory.getLogger(workflowClass)

  private val context = new WorkflowContextImpl(workflowId, regionInfo.selfRegion)

  private val router =
    new ReflectiveWorkflowRouter[S, W](context, instanceFactory, componentDescriptor.methodInvokers, serializer)

  override def configuration: SpiWorkflow.WorkflowConfig = {
    val workflow = instanceFactory(context)
    val definition = workflow.definition()

    def toRecovery(sdkRecoverStrategy: SdkRecoverStrategy[_]): SpiWorkflow.RecoverStrategy = {

      val stepTransition = new SpiWorkflow.StepTransition(
        sdkRecoverStrategy.failoverStepName,
        sdkRecoverStrategy.failoverStepInput.toScala.map(serializer.toBytes))
      new SpiWorkflow.RecoverStrategy(sdkRecoverStrategy.maxRetries, failoverTo = stepTransition)
    }

    val stepConfigs =
      definition.getStepConfigs.asScala.map { config =>
        val stepTimeout = config.timeout.toScala.map(_.toScala)
        val failoverRecoverStrategy = config.recoverStrategy.toScala.map(toRecovery)
        (config.stepName, new SpiWorkflow.StepConfig(config.stepName, stepTimeout, failoverRecoverStrategy))
      }.toMap

    val defaultStepRecoverStrategy = definition.getStepRecoverStrategy.toScala.map(toRecovery)

    val failoverRecoverStrategy = definition.getFailoverStepName.toScala.map(stepName =>
      //when failoverStepName exists, maxRetries must exist
      new SpiWorkflow.RecoverStrategy(
        definition.getFailoverMaxRetries.toScala.get.maxRetries,
        new SpiWorkflow.StepTransition(stepName, definition.getFailoverStepInput.toScala.map(serializer.toBytes))))

    val stepTimeout = definition.getStepTimeout.toScala.map(_.toScala)

    new SpiWorkflow.WorkflowConfig(
      workflowTimeout = definition.getWorkflowTimeout.toScala.map(_.toScala),
      failoverRecoverStrategy = failoverRecoverStrategy,
      defaultStepTimeout = stepTimeout,
      defaultStepRecoverStrategy = defaultStepRecoverStrategy,
      stepConfigs = stepConfigs)
  }

  private def commandContext(commandName: String, metadata: Metadata = MetadataImpl.Empty) =
    new CommandContextImpl(
      workflowId,
      commandName,
      regionInfo.selfRegion,
      metadata,
      // FIXME we'd need to start a parent span for the command here to have one to base custom user spans of off?
      None,
      tracerFactory)

  private def toSpiTransition(transition: Transition): SpiWorkflow.Transition =
    transition match {
      case StepTransition(stepName, input) =>
        new SpiWorkflow.StepTransition(stepName, input.map(serializer.toBytes))
      case Pause        => SpiWorkflow.Pause
      case NoTransition => SpiWorkflow.NoTransition
      case End          => SpiWorkflow.End
    }

  private def handleState(persistence: Persistence[Any]): SpiWorkflow.Persistence =
    persistence match {
      case UpdateState(newState) => new SpiWorkflow.UpdateState(serializer.toBytes(newState))
      case DeleteState           => SpiWorkflow.DeleteState
      case NoPersistence         => SpiWorkflow.NoPersistence
    }

  private def toSpiCommandEffect(effect: Workflow.Effect[_]): SpiWorkflow.CommandEffect = {

    effect match {
      case error: ErrorEffectImpl[_] =>
        new SpiWorkflow.ErrorEffect(new SpiEntity.Error(error.description))

      case WorkflowEffectImpl(persistence, transition, reply) =>
        val (replyBytes, spiMetadata) =
          reply match {
            case ReplyValue(value, metadata) => (serializer.toBytes(value), MetadataImpl.toSpi(metadata))
            // FIXME: WorkflowEffectImpl never contain a NoReply
            case NoReply => (BytesPayload.empty, SpiMetadata.empty)
          }

        val spiTransition = toSpiTransition(transition)

        handleState(persistence) match {
          case upt: SpiWorkflow.UpdateState =>
            new SpiWorkflow.CommandTransitionalEffect(upt, spiTransition, replyBytes, spiMetadata)

          case SpiWorkflow.NoPersistence =>
            // no persistence and no transition, is a reply only effect
            if (spiTransition == SpiWorkflow.NoTransition)
              new SpiWorkflow.ReadOnlyEffect(replyBytes, spiMetadata)
            else
              new SpiWorkflow.CommandTransitionalEffect(
                SpiWorkflow.NoPersistence,
                spiTransition,
                replyBytes,
                spiMetadata)

          case SpiWorkflow.DeleteState =>
            // TODO: delete not yet supported, therefore always ReplyEffect
            throw new IllegalArgumentException("State deletion not supported yet")

        }

      case TransitionalEffectImpl(persistence, transition) =>
        // Adding for matching completeness can't happen. Typed API blocks this case.
        throw new IllegalArgumentException("Received transitional effect while processing a command")
    }
  }

  private def toSpiTransitionalEffect(effect: Workflow.Effect.TransitionalEffect[_]) =
    effect match {
      case trEff: TransitionalEffectImpl[_, _] =>
        new SpiWorkflow.TransitionalOnlyEffect(handleState(trEff.persistence), toSpiTransition(trEff.transition))
    }

  override def handleCommand(
      userState: Option[SpiWorkflow.State],
      command: SpiEntity.Command): Future[SpiWorkflow.CommandEffect] = {

    val metadata = MetadataImpl.of(command.metadata)
    val context = commandContext(command.name, metadata)

    val timerScheduler =
      new TimerSchedulerImpl(timerClient, context.componentCallMetadata)

    // smuggling 0 arity method called from component client through here
    val cmd = command.payload.getOrElse(BytesPayload.empty)

    val CommandResult(effect) =
      try {
        router.handleCommand(
          userState = userState,
          commandName = command.name,
          command = cmd,
          context = context,
          timerScheduler = timerScheduler)
      } catch {
        case e: HandlerNotFoundException =>
          throw WorkflowException(workflowId, command.name, e.getMessage, Some(e))
        case BadRequestException(msg) => CommandResult(WorkflowEffectImpl[Any]().error(msg))
        case e: WorkflowException     => throw e
        case NonFatal(error) =>
          throw WorkflowException(workflowId, command.name, s"Unexpected failure: $error", Some(error))
      }

    Future.successful(toSpiCommandEffect(effect))
  }

  override def executeStep(
      stepName: String,
      input: Option[BytesPayload],
      userState: Option[BytesPayload]): Future[BytesPayload] = {

    val context = commandContext(stepName)
    val timerScheduler =
      new TimerSchedulerImpl(timerClient, context.componentCallMetadata)

    try {
      val handleStep = router.handleStep(
        userState,
        input = input,
        stepName = stepName,
        timerScheduler = timerScheduler,
        commandContext = context,
        executionContext = sdkExecutionContext)
      handleStep.onComplete {
        case Failure(exception) => log.error(s"Workflow [$workflowId], failed to execute step [$stepName]", exception)
        case Success(_)         =>
      }(sdkExecutionContext)
      handleStep
    } catch {
      case NonFatal(ex) =>
        throw WorkflowException(s"unexpected exception [${ex.getMessage}] while executing step [$stepName]", Some(ex))
    }
  }

  override def transition(
      stepName: String,
      result: Option[BytesPayload],
      userState: Option[BytesPayload]): Future[SpiWorkflow.TransitionalOnlyEffect] = {
    val TransitionalResult(effect) =
      try {
        router.getNextStep(stepName, result.get, userState)
      } catch {
        case NonFatal(ex) =>
          log.error(s"Workflow [$workflowId], failed to transition from step [$stepName]", ex)
          throw WorkflowException(
            s"unexpected exception [${ex.getMessage}] while executing transition for step [$stepName]",
            Some(ex))
      }
    Future.successful(toSpiTransitionalEffect(effect))
  }

}

/**
 * INTERNAL API
 */
@InternalApi
private[akka] final class CommandContextImpl(
    override val workflowId: String,
    override val commandName: String,
    override val selfRegion: String,
    override val metadata: Metadata,
    span: Option[Span],
    tracerFactory: () => Tracer)
    extends AbstractContext
    with CommandContext
    with ActivatableContext {

  override def tracing(): Tracing =
    new SpanTracingImpl(span, tracerFactory)

  override def commandId(): Long = 0
}

/**
 * INTERNAL API
 */
@InternalApi
private[akka] final class WorkflowContextImpl(override val workflowId: String, override val selfRegion: String)
    extends AbstractContext
    with WorkflowContext
