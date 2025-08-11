/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
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
import akka.javasdk.CommandException
import akka.javasdk.Metadata
import akka.javasdk.Tracing
import akka.javasdk.impl.AbstractContext
import akka.javasdk.impl.ComponentDescriptor
import akka.javasdk.impl.ComponentType
import akka.javasdk.impl.ErrorHandling.BadRequestException
import akka.javasdk.impl.HandlerNotFoundException
import akka.javasdk.impl.MetadataImpl
import akka.javasdk.impl.WorkflowExceptions.WorkflowException
import akka.javasdk.impl.serialization.JsonSerializer
import akka.javasdk.impl.telemetry.SpanTracingImpl
import akka.javasdk.impl.telemetry.Telemetry
import akka.javasdk.impl.telemetry.TraceInstrumentation
import akka.javasdk.impl.telemetry.WorkflowCategory
import akka.javasdk.impl.timer.TimerSchedulerImpl
import akka.javasdk.workflow.CommandContext
import akka.javasdk.workflow.Workflow
import akka.javasdk.workflow.Workflow.LegacyWorkflowTimeout
import akka.javasdk.workflow.Workflow.{ RecoverStrategy => SdkRecoverStrategy }
import akka.javasdk.workflow.WorkflowContext
import akka.runtime.sdk.spi.BytesPayload
import akka.runtime.sdk.spi.RegionInfo
import akka.runtime.sdk.spi.SpiEntity
import akka.runtime.sdk.spi.SpiMetadata
import akka.runtime.sdk.spi.SpiWorkflow
import akka.runtime.sdk.spi.SpiWorkflow.StepCallReply
import akka.runtime.sdk.spi.SpiWorkflow.StepCommand
import akka.runtime.sdk.spi.SpiWorkflow.StepResult
import akka.runtime.sdk.spi.TimerClient
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC

/**
 * INTERNAL API
 */
@InternalApi
class WorkflowImpl[S, W <: Workflow[S]](
    componentId: String,
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

  private val traceInstrumentation = new TraceInstrumentation(componentId, WorkflowCategory, tracerFactory)

  private val router =
    new ReflectiveWorkflowRouter[S, W](instanceFactory, componentDescriptor.methodInvokers, serializer)

  override def configuration: SpiWorkflow.WorkflowConfig = {
    val workflowContext = new WorkflowContextImpl(workflowId, regionInfo.selfRegion, None, tracerFactory)
    val workflow = instanceFactory(workflowContext)
    val workflowConfig = workflow.settings()

    def toRecovery(sdkRecoverStrategy: SdkRecoverStrategy[_]): SpiWorkflow.RecoverStrategy = {

      val stepTransition = new SpiWorkflow.StepTransition(
        sdkRecoverStrategy.failoverStepName,
        sdkRecoverStrategy.failoverStepInput.toScala.map(serializer.toBytes))
      new SpiWorkflow.RecoverStrategy(sdkRecoverStrategy.maxRetries, failoverTo = stepTransition)
    }

    val stepConfigs =
      workflowConfig.stepSettings.asScala.map { stepSettings =>
        val stepTimeout = stepSettings.timeout.toScala.map(_.toScala)
        val failoverRecoverStrategy = stepSettings.recovery.toScala.map(toRecovery)
        (stepSettings.stepName, new SpiWorkflow.StepConfig(stepSettings.stepName, stepTimeout, failoverRecoverStrategy))
      }.toMap

    val (workflowTimeout, workflowRecoverStrategy) =
      workflowConfig match {
        case c: LegacyWorkflowTimeout =>
          (c.workflowTimeout.toScala.map(_.toScala), c.workflowRecoverStrategy().toScala.map(toRecovery))
        case _ => (None, None)
      }

    val defaultStepTimeout = workflowConfig.defaultStepTimeout().toScala.map(_.toScala)
    val defaultStepRecoverStrategy = workflowConfig.defaultStepRecoverStrategy.toScala.map(toRecovery)

    new SpiWorkflow.WorkflowConfig(
      workflowTimeout = workflowTimeout,
      failoverRecoverStrategy = workflowRecoverStrategy,
      defaultStepTimeout = defaultStepTimeout,
      defaultStepRecoverStrategy = defaultStepRecoverStrategy,
      stepConfigs = stepConfigs)
  }

  private def commandContext(commandName: String, span: Option[Span], metadata: Metadata) =
    new CommandContextImpl(workflowId, commandName, regionInfo.selfRegion, metadata, span, tracerFactory)

  override def handleCommand(
      userState: Option[SpiWorkflow.State],
      command: SpiEntity.Command): Future[SpiWorkflow.CommandEffect] = {

    val span: Option[Span] =
      traceInstrumentation.buildEntityCommandSpan(ComponentType.Workflow, componentId, workflowId, command)
    span.foreach(s => MDC.put(Telemetry.TRACE_ID, s.getSpanContext.getTraceId))
    val workflowContext = new WorkflowContextImpl(workflowId, regionInfo.selfRegion, span, tracerFactory)

    val metadata = MetadataImpl.of(command.metadata)
    val context = commandContext(command.name, span, metadata)

    val timerScheduler =
      new TimerSchedulerImpl(timerClient, context.componentCallMetadata)

    // smuggling 0 arity method called from component client through here
    val cmd = command.payload.getOrElse(BytesPayload.empty)

    try {
      val effect = router.handleCommand(
        userState = userState,
        commandName = command.name,
        command = cmd,
        context = context,
        timerScheduler = timerScheduler,
        deleted = command.isDeleted,
        workflowContext)
      Future.successful(effect)
    } catch {
      case e: CommandException =>
        val serializedException = serializer.toBytes(e)
        Future.successful(new SpiWorkflow.ErrorEffect(new SpiEntity.Error(e.getMessage, Some(serializedException))))
      case e: HandlerNotFoundException =>
        throw WorkflowException(workflowId, command.name, e.getMessage, Some(e))
      case BadRequestException(msg) =>
        Future.successful(new SpiWorkflow.ErrorEffect(new SpiEntity.Error(msg, None)))
      case e: WorkflowException => throw e
      case NonFatal(error) =>
        throw WorkflowException(workflowId, command.name, s"Unexpected failure: $error", Some(error))
    } finally {
      span.foreach { s =>
        MDC.remove(Telemetry.TRACE_ID)
        s.end()
      }
    }

  }

  override def invokeStep(userState: Option[BytesPayload], stepCommand: StepCommand): Future[StepResult] = {

    val stepName = stepCommand.stepName
    val span: Option[Span] =
      traceInstrumentation.buildEntityCommandSpan(
        ComponentType.Workflow,
        componentId,
        workflowId,
        stepName,
        stepCommand.metadata)
    span.foreach(s => MDC.put(Telemetry.TRACE_ID, s.getSpanContext.getTraceId))
    val workflowContext = new WorkflowContextImpl(workflowId, regionInfo.selfRegion, span, tracerFactory)

    val context = commandContext(stepName, span, MetadataImpl.of(stepCommand.metadata))
    val timerScheduler =
      new TimerSchedulerImpl(timerClient, context.componentCallMetadata)

    try {
      val handleStep = router.handleStep(
        userState,
        input = stepCommand.input,
        stepName = stepName,
        timerScheduler = timerScheduler,
        commandContext = context,
        executionContext = sdkExecutionContext,
        workflowContext)

      handleStep.onComplete {
        case Failure(exception) =>
          span.foreach { s =>
            s.setStatus(StatusCode.ERROR) //TODO doesn't work, not sure why the span is presented the same way
            s.end()
          }
          log.error(s"Workflow [$workflowId], failed to execute step [$stepName]", exception)
        case Success(_) =>
          span.foreach(_.end())
      }(sdkExecutionContext)

      handleStep

    } catch {
      case NonFatal(ex) =>
        val message = s"unexpected exception [${ex.getMessage}] while executing step [$stepName]"
        log.error(message, ex)
        span.foreach(_.end())
        throw WorkflowException(message, Some(ex))
    } finally {
      span.foreach { __ =>
        MDC.remove(Telemetry.TRACE_ID)
      //ending the span is done in the onComplete above, can't be here because the Future may not complete
      }
    }
  }

  override def transition(
      stepName: String,
      result: Option[BytesPayload],
      userState: Option[BytesPayload]): Future[SpiWorkflow.TransitionalOnlyEffect] = {
    val effect =
      try {
        val workflowContext = new WorkflowContextImpl(workflowId, regionInfo.selfRegion, None, tracerFactory)
        router.getNextStep(stepName, result.get, userState, workflowContext)
      } catch {
        case NonFatal(ex) =>
          log.error(s"Workflow [$workflowId], failed to transition from step [$stepName]", ex)
          throw WorkflowException(
            s"unexpected exception [${ex.getMessage}] while executing transition for step [$stepName]",
            Some(ex))
      }
    Future.successful(effect)
  }

  override def executeStep(
      stepName: String,
      input: Option[BytesPayload],
      userState: Option[BytesPayload]): Future[BytesPayload] = {
    executeStep(userState, new SpiWorkflow.StepCommand(stepName, input, SpiMetadata.empty))
  }

  override def executeStep(userState: Option[BytesPayload], stepCommand: StepCommand): Future[BytesPayload] =
    invokeStep(userState, stepCommand).map {
      case stepReply: StepCallReply => stepReply.reply
      case _                        => throw new IllegalArgumentException(s"Unexpected result from step [$stepCommand]")
    }(sdkExecutionContext)
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
    with CommandContext {

  override def tracing(): Tracing =
    new SpanTracingImpl(span, tracerFactory)

  override def commandId(): Long = 0
}

/**
 * INTERNAL API
 */
@InternalApi
private[akka] final class WorkflowContextImpl(
    override val workflowId: String,
    override val selfRegion: String,
    val span: Option[Span],
    tracerFactory: () => Tracer)
    extends AbstractContext
    with WorkflowContext {

  override def tracing(): Tracing = new SpanTracingImpl(span, tracerFactory)
}
