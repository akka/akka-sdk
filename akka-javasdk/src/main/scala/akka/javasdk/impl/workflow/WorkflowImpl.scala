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

import akka.actor.typed.ActorSystem
import akka.annotation.InternalApi
import akka.javasdk.CommandException
import akka.javasdk.Metadata
import akka.javasdk.Tracing
import akka.javasdk.impl.AbstractContext
import akka.javasdk.impl.ComponentDescriptor
import akka.javasdk.impl.ErrorHandling.BadRequestException
import akka.javasdk.impl.HandlerNotFoundException
import akka.javasdk.impl.MetadataImpl
import akka.javasdk.impl.WorkflowExceptions.WorkflowException
import akka.javasdk.impl.client.MethodRefResolver
import akka.javasdk.impl.serialization.JsonSerializer
import akka.javasdk.impl.telemetry.SpanTracingImpl
import akka.javasdk.impl.telemetry.Telemetry
import akka.javasdk.impl.timer.TimerSchedulerImpl
import akka.javasdk.workflow.CommandContext
import akka.javasdk.workflow.Workflow
import akka.javasdk.workflow.Workflow.LegacyWorkflowTimeout
import akka.javasdk.workflow.Workflow.StepHandler.BinaryStepHandler
import akka.javasdk.workflow.Workflow.StepHandler.UnaryStepHandler
import akka.javasdk.workflow.Workflow.WorkflowSettings
import akka.javasdk.workflow.Workflow.{ RecoverStrategy => SdkRecoverStrategy }
import akka.javasdk.workflow.WorkflowContext
import akka.runtime.sdk.spi.BytesPayload
import akka.runtime.sdk.spi.ComponentClients
import akka.runtime.sdk.spi.RegionInfo
import akka.runtime.sdk.spi.SpiEntity
import akka.runtime.sdk.spi.SpiMetadata
import akka.runtime.sdk.spi.SpiWorkflow
import akka.runtime.sdk.spi.SpiWorkflow.StepCallReply
import akka.runtime.sdk.spi.SpiWorkflow.StepCommand
import akka.runtime.sdk.spi.SpiWorkflow.StepResult
import akka.runtime.sdk.spi.TimerClient
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.{ Context => OtelContext }
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
    runtimeComponentClients: ComponentClients,
    instanceFactory: Function[WorkflowContext, W])(implicit system: ActorSystem[_])
    extends SpiWorkflow {

  private val log: Logger = LoggerFactory.getLogger(workflowClass)

  private val router =
    new ReflectiveWorkflowRouter[S, W](
      instanceFactory,
      componentDescriptor.methodInvokers,
      serializer,
      sdkExecutionContext,
      runtimeComponentClients)

  override def configuration: SpiWorkflow.WorkflowConfig = {
    val workflowContext = new WorkflowContextImpl(workflowId, regionInfo.selfRegion, None, tracerFactory)
    val workflow = instanceFactory(workflowContext)
    val workflowConfig = workflow.settings()

    def validateStep(stepLambda: Any) = {
      val method = MethodRefResolver.resolveMethodRef(stepLambda)
      val stepName = WorkflowDescriptor.stepMethodName(method)
      val classToTest = method.getDeclaringClass
      if (!classToTest.isAssignableFrom(workflow.getClass)) {
        throw new IllegalArgumentException(
          s"Workflow [${workflow.getClass.getName}] settings refers to step [$stepName] from another class [${classToTest.getName}], which is not allowed.")
      }
    }

    def toRecovery(sdkRecoverStrategy: SdkRecoverStrategy[_]): SpiWorkflow.RecoverStrategy = {

      sdkRecoverStrategy.stepHandler().toScala.foreach {
        case handler: UnaryStepHandler  => validateStep(handler.handler())
        case handler: BinaryStepHandler => validateStep(handler.handler())
      }

      val stepTransition = new SpiWorkflow.StepTransition(
        sdkRecoverStrategy.failoverStepName,
        sdkRecoverStrategy.failoverStepInput.toScala.map(serializer.toBytes))
      new SpiWorkflow.RecoverStrategy(sdkRecoverStrategy.maxRetries, failoverTo = stepTransition)
    }

    val stepConfigs =
      workflowConfig.stepSettings.asScala.map { stepSettings =>
        stepSettings.stepLambda().toScala.foreach(validateStep)
        val stepTimeout = stepSettings.timeout.toScala.map(_.toScala)
        val failoverRecoverStrategy = stepSettings.recovery.toScala.map(toRecovery)
        (stepSettings.stepName, new SpiWorkflow.StepConfig(stepSettings.stepName, stepTimeout, failoverRecoverStrategy))
      }.toMap

    val (workflowTimeout, workflowRecoverStrategy) =
      workflowConfig match {
        case c: LegacyWorkflowTimeout =>
          (c.workflowTimeout.toScala.map(_.toScala), c.workflowRecoverStrategy().toScala.map(toRecovery))
        case s: WorkflowSettings =>
          (s.workflowTimeout.toScala.map(_.toScala), s.workflowRecoverStrategy().toScala.map(toRecovery))
      }

    val defaultStepTimeout = workflowConfig.defaultStepTimeout().toScala.map(_.toScala)
    val defaultStepRecoverStrategy = workflowConfig.defaultStepRecoverStrategy.toScala.map(toRecovery)

    new SpiWorkflow.WorkflowConfig(
      workflowTimeout = workflowTimeout,
      failoverRecoverStrategy = workflowRecoverStrategy,
      defaultStepTimeout = defaultStepTimeout,
      defaultStepRecoverStrategy = defaultStepRecoverStrategy,
      stepConfigs = stepConfigs,
      passivationDelay = workflowConfig.passivationDelay().toScala.map(_.toScala))
  }

  private def commandContext(commandName: String, telemetryContext: Option[OtelContext], metadata: Metadata) =
    new CommandContextImpl(workflowId, commandName, regionInfo.selfRegion, metadata, telemetryContext, tracerFactory)

  override def handleCommand(
      userState: Option[SpiWorkflow.State],
      command: SpiEntity.Command): Future[SpiWorkflow.CommandEffect] = {

    val telemetryContext = Option(command.telemetryContext)
    val traceId = telemetryContext.flatMap { context =>
      Option(Span.fromContextOrNull(context)).map(_.getSpanContext.getTraceId)
    }
    traceId.foreach(id => MDC.put(Telemetry.TRACE_ID, id))

    val workflowContext = new WorkflowContextImpl(workflowId, regionInfo.selfRegion, telemetryContext, tracerFactory)

    val metadata = MetadataImpl.of(command.metadata)
    val context = commandContext(command.name, telemetryContext, metadata)

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
        throw WorkflowException(workflowId, command.name, s"unexpected failure: $error", Some(error))
    } finally {
      if (traceId.isDefined) MDC.remove(Telemetry.TRACE_ID)
    }

  }

  override def invokeStep(userState: Option[BytesPayload], stepCommand: StepCommand): Future[StepResult] = {

    val stepName = stepCommand.stepName
    val telemetryContext = Option(stepCommand.telemetryContext)
    val traceId = telemetryContext.flatMap { context =>
      Option(Span.fromContextOrNull(context)).map(_.getSpanContext.getTraceId)
    }
    traceId.foreach(id => MDC.put(Telemetry.TRACE_ID, id))
    val workflowContext = new WorkflowContextImpl(workflowId, regionInfo.selfRegion, telemetryContext, tracerFactory)

    val context = commandContext(stepName, telemetryContext, MetadataImpl.of(stepCommand.metadata))
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
          log.error(s"Workflow [$workflowId], failed to execute step [$stepName]", exception)
        case Success(_) =>
      }(sdkExecutionContext)

      handleStep

    } catch {
      case NonFatal(ex) =>
        val message = s"unexpected exception [${ex.getMessage}] while executing step [$stepName]"
        log.error(message, ex)
        throw WorkflowException(message, Some(ex))
    } finally {
      if (traceId.isDefined) MDC.remove(Telemetry.TRACE_ID)
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
    telemetryContext: Option[OtelContext],
    tracerFactory: () => Tracer)
    extends AbstractContext
    with CommandContext {

  override def tracing(): Tracing =
    new SpanTracingImpl(telemetryContext, tracerFactory)

  override def commandId(): Long = 0
}

/**
 * INTERNAL API
 */
@InternalApi
private[akka] final class WorkflowContextImpl(
    override val workflowId: String,
    override val selfRegion: String,
    val telemetryContext: Option[OtelContext],
    tracerFactory: () => Tracer)
    extends AbstractContext
    with WorkflowContext {

  override def tracing(): Tracing = new SpanTracingImpl(telemetryContext, tracerFactory)
}
