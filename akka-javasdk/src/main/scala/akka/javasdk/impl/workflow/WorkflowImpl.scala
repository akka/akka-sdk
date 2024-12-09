/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.workflow

import akka.NotUsed
import akka.annotation.InternalApi
import akka.javasdk.Metadata
import akka.javasdk.Tracing
import akka.javasdk.impl.AbstractContext
import akka.javasdk.impl.ActivatableContext
import akka.javasdk.impl.AnySupport
import akka.javasdk.impl.ErrorHandling
import akka.javasdk.impl.ErrorHandling.BadRequestException
import akka.javasdk.impl.MetadataImpl
import akka.javasdk.impl.Service
import akka.javasdk.impl.WorkflowExceptions.ProtocolException
import akka.javasdk.impl.WorkflowExceptions.WorkflowException
import akka.javasdk.impl.WorkflowExceptions.failureMessageForLog
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
import akka.javasdk.impl.workflow.WorkflowEffectImpl.Reply
import akka.javasdk.impl.workflow.WorkflowEffectImpl.ReplyValue
import akka.javasdk.impl.workflow.WorkflowEffectImpl.StepTransition
import akka.javasdk.impl.workflow.WorkflowEffectImpl.TransitionalEffectImpl
import akka.javasdk.impl.workflow.WorkflowEffectImpl.UpdateState
import akka.javasdk.impl.workflow.WorkflowRouter.CommandResult
import akka.javasdk.workflow.CommandContext
import akka.javasdk.workflow.Workflow
import akka.javasdk.workflow.Workflow.WorkflowDef
import akka.javasdk.workflow.WorkflowContext
import akka.runtime.sdk.spi.TimerClient
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Source
import com.google.protobuf.ByteString
import com.google.protobuf.any.{ Any => ScalaPbAny }
import com.google.protobuf.duration
import com.google.protobuf.duration.Duration
import io.grpc.Status
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import kalix.protocol.component
import kalix.protocol.component.{ Reply => ProtoReply }
import kalix.protocol.workflow_entity.RecoverStrategy
import kalix.protocol.workflow_entity.StepConfig
import kalix.protocol.workflow_entity.WorkflowClientAction
import kalix.protocol.workflow_entity.WorkflowConfig
import kalix.protocol.workflow_entity.WorkflowEffect
import kalix.protocol.workflow_entity.WorkflowEntities
import kalix.protocol.workflow_entity.WorkflowEntityInit
import kalix.protocol.workflow_entity.WorkflowStreamIn
import kalix.protocol.workflow_entity.WorkflowStreamIn.Message
import kalix.protocol.workflow_entity.WorkflowStreamIn.Message.Empty
import kalix.protocol.workflow_entity.WorkflowStreamIn.Message.Init
import kalix.protocol.workflow_entity.WorkflowStreamIn.Message.Step
import kalix.protocol.workflow_entity.WorkflowStreamIn.Message.Transition
import kalix.protocol.workflow_entity.WorkflowStreamIn.Message.{ Command => InCommand }
import kalix.protocol.workflow_entity.WorkflowStreamOut
import kalix.protocol.workflow_entity.WorkflowStreamOut.Message.{ Failure => OutFailure }
import kalix.protocol.workflow_entity.{ EndTransition => ProtoEndTransition }
import kalix.protocol.workflow_entity.{ NoTransition => ProtoNoTransition }
import kalix.protocol.workflow_entity.{ Pause => ProtoPause }
import kalix.protocol.workflow_entity.{ StepTransition => ProtoStepTransition }
import org.slf4j.LoggerFactory
import java.util.Optional

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._
import scala.language.existentials
import scala.util.control.NonFatal

import akka.javasdk.impl.serialization.JsonSerializer

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
    new ReflectiveWorkflowRouter[S, W](instanceFactory(context), componentDescriptor.commandHandlers)

}

/**
 * INTERNAL API
 */
@InternalApi
final class WorkflowImpl(
    val services: Map[String, WorkflowService[_, _]],
    timerClient: TimerClient,
    sdkExcutionContext: ExecutionContext,
    sdkDispatcherName: String,
    tracerFactory: () => Tracer)
    extends kalix.protocol.workflow_entity.WorkflowEntities {

  private implicit val ec: ExecutionContext = sdkExcutionContext
  private final val log = LoggerFactory.getLogger(this.getClass)

  override def handle(in: Source[WorkflowStreamIn, NotUsed]): Source[WorkflowStreamOut, NotUsed] =
    in.prefixAndTail(1)
      .flatMapConcat {
        case (Seq(WorkflowStreamIn(Init(init), _)), source) =>
          val (flow, config) = runWorkflow(init)
          Source.single(config).concat(source.via(flow))

        case (Seq(), _) =>
          // if error during recovery in runtime the stream will be completed before init
          log.warn("Workflow stream closed before init.")
          Source.empty[WorkflowStreamOut]

        case (Seq(WorkflowStreamIn(other, _)), _) =>
          throw ProtocolException(s"Expected init message for Workflow, but received [${other.getClass.getName}]")
      }
      .recover { case error =>
        ErrorHandling.withCorrelationId { correlationId =>
          log.error(failureMessageForLog(error), error)
          toFailureOut(error, correlationId)
        }
      }
      .async(sdkDispatcherName)

  private def toFailureOut(error: Throwable, correlationId: String) = {
    error match {
      case WorkflowException(workflowId, commandId, commandName, _, _) =>
        WorkflowStreamOut(
          OutFailure(
            component.Failure(
              commandId = commandId,
              description = s"Unexpected workflow [$workflowId] error for command [$commandName] [$correlationId]")))
      case _ =>
        WorkflowStreamOut(OutFailure(component.Failure(description = s"Unexpected error [$correlationId]")))
    }
  }

  private def toRecoverStrategy(serializer: JsonSerializer)(
      recoverStrategy: Workflow.RecoverStrategy[_]): RecoverStrategy = {
    RecoverStrategy(
      maxRetries = recoverStrategy.maxRetries,
      failoverTo = Some(
        ProtoStepTransition(
          recoverStrategy.failoverStepName,
          recoverStrategy.failoverStepInput.toScala.map { a =>
            val bytesPayload = serializer.toBytes(a)
            AnySupport.toScalaPbAny(bytesPayload)
          })))
  }

  private def toStepConfig(
      name: String,
      timeout: Optional[java.time.Duration],
      recoverStrategy: Option[Workflow.RecoverStrategy[_]],
      serializer: JsonSerializer) = {
    val stepTimeout = timeout.toScala.map(duration.Duration(_))
    val stepRecoverStrategy = recoverStrategy.map(toRecoverStrategy(serializer))
    StepConfig(name, stepTimeout, stepRecoverStrategy)
  }

  private def toWorkflowConfig(workflowDefinition: WorkflowDef[_], serializer: JsonSerializer): WorkflowConfig = {
    val workflowTimeout = workflowDefinition.getWorkflowTimeout.toScala.map(Duration(_))
    val stepConfigs = workflowDefinition.getStepConfigs.asScala
      .map(c => toStepConfig(c.stepName, c.timeout, c.recoverStrategy.toScala, serializer))
      .toSeq
    val stepConfig =
      toStepConfig("", workflowDefinition.getStepTimeout, workflowDefinition.getStepRecoverStrategy.toScala, serializer)

    val failoverTo = workflowDefinition.getFailoverStepName.toScala.map(stepName => {
      ProtoStepTransition(
        stepName,
        workflowDefinition.getFailoverStepInput.toScala.map { a =>
          val bytesPayload = serializer.toBytes(a)
          AnySupport.toScalaPbAny(bytesPayload)
        })
    })

    val failoverRecovery =
      workflowDefinition.getFailoverMaxRetries.toScala.map(strategy => RecoverStrategy(strategy.getMaxRetries))

    WorkflowConfig(workflowTimeout, failoverTo, failoverRecovery, Some(stepConfig), stepConfigs)
  }

  private def runWorkflow(
      init: WorkflowEntityInit): (Flow[WorkflowStreamIn, WorkflowStreamOut, NotUsed], WorkflowStreamOut) = {
    val service =
      services.getOrElse(init.serviceName, throw ProtocolException(init, s"Service not found: ${init.serviceName}"))
    val router: WorkflowRouter[_, _] =
      service.createRouter(new WorkflowContextImpl(init.entityId))
    val workflowId = init.entityId

    val workflowConfig =
      WorkflowStreamOut(
        WorkflowStreamOut.Message.Config(toWorkflowConfig(router._getWorkflowDefinition(), service.serializer)))

    init.userState match {
      case Some(state) =>
        val bytesPayload = AnySupport.toSpiBytesPayload(state)
        val decoded = service.serializer.fromBytes(bytesPayload)
        router._internalSetInitState(decoded, init.finished)
      case None => // no initial state
    }

    def toProtoEffect(effect: Workflow.Effect[_], commandId: Long, errorCode: Option[Status.Code]) = {

      def effectMessage[R](persistence: Persistence[_], transition: WorkflowEffectImpl.Transition, reply: Reply[R]) = {

        val protoEffect =
          persistence match {
            case UpdateState(newState) =>
              router._internalSetInitState(newState, transition.isInstanceOf[End.type])
              val bytesPayload = service.serializer.toBytes(newState)
              val pbAny = AnySupport.toScalaPbAny(bytesPayload)
              WorkflowEffect.defaultInstance.withUserState(pbAny)
            // TODO: persistence should be optional, but we must ensure that we don't save it back to null
            // and preferably we should not even send it over the wire.
            case NoPersistence => WorkflowEffect.defaultInstance
            case DeleteState   => throw new RuntimeException("Workflow state deleted not yet supported")
          }

        val toProtoTransition =
          transition match {
            case StepTransition(stepName, input) =>
              WorkflowEffect.Transition.StepTransition(
                ProtoStepTransition(
                  stepName,
                  input.map { a =>
                    val bytesPayload = service.serializer.toBytes(a)
                    AnySupport.toScalaPbAny(bytesPayload)
                  }))
            case Pause        => WorkflowEffect.Transition.Pause(ProtoPause.defaultInstance)
            case NoTransition => WorkflowEffect.Transition.NoTransition(ProtoNoTransition.defaultInstance)
            case End          => WorkflowEffect.Transition.EndTransition(ProtoEndTransition.defaultInstance)
          }

        val clientAction = {
          val protoReply =
            reply match {
              case ReplyValue(value, metadata) =>
                val bytesPayload = service.serializer.toBytes(value)
                val pbAny = AnySupport.toScalaPbAny(bytesPayload)
                ProtoReply(payload = Some(pbAny), metadata = MetadataImpl.toProtocol(metadata))
              case NoReply => ProtoReply.defaultInstance
            }
          WorkflowClientAction.defaultInstance.withReply(protoReply)
        }
        protoEffect
          .withTransition(toProtoTransition)
          .withClientAction(clientAction)
      }

      effect match {
        case error: ErrorEffectImpl[_] =>
          val finalCode = error.status.orElse(errorCode).getOrElse(Status.Code.UNKNOWN)
          val statusCode = finalCode.value()
          val failure = component.Failure(commandId, error.description, statusCode)
          val failureClientAction = WorkflowClientAction.defaultInstance.withFailure(failure)
          val noTransition = WorkflowEffect.Transition.NoTransition(ProtoNoTransition.defaultInstance)
          val failureEffect = WorkflowEffect.defaultInstance
            .withClientAction(failureClientAction)
            .withTransition(noTransition)
            .withCommandId(commandId)
          WorkflowStreamOut(WorkflowStreamOut.Message.Effect(failureEffect))

        case WorkflowEffectImpl(persistence, transition, reply) =>
          val protoEffect =
            effectMessage(persistence, transition, reply)
              .withCommandId(commandId)
          WorkflowStreamOut(WorkflowStreamOut.Message.Effect(protoEffect))

        case TransitionalEffectImpl(persistence, transition) =>
          val protoEffect =
            effectMessage(persistence, transition, NoReply)
              .withCommandId(commandId)
          WorkflowStreamOut(WorkflowStreamOut.Message.Effect(protoEffect))
      }
    }

    val flow = Flow[WorkflowStreamIn]
      .map(_.message)
      .mapAsync(1) {

        case InCommand(command) if workflowId != command.entityId =>
          Future.failed(ProtocolException(command, "Receiving Workflow is not the intended recipient of command"))

        case InCommand(command) =>
          val metadata = MetadataImpl.of(command.metadata.map(_.entries.toVector).getOrElse(Nil))

          val context =
            new CommandContextImpl(
              workflowId,
              command.name,
              command.id,
              metadata,
              // FIXME we'd need to start a parent span for the command here to have one to base custom user spans of off?
              None,
              tracerFactory)
          val timerScheduler =
            new TimerSchedulerImpl(timerClient, context.componentCallMetadata)

          val cmdPayloadPbAny = command.payload.getOrElse(
            // FIXME smuggling 0 arity method called from component client through here
            ScalaPbAny.defaultInstance.withTypeUrl(AnySupport.JsonTypeUrlPrefix).withValue(ByteString.empty()))

          val (CommandResult(effect), errorCode) =
            try {
              (router._internalHandleCommand(command.name, cmdPayloadPbAny, context, timerScheduler), None)
            } catch {
              case BadRequestException(msg) =>
                (CommandResult(WorkflowEffectImpl[Any]().error(msg)), Some(Status.Code.INVALID_ARGUMENT))
              case e: WorkflowException => throw e
              case NonFatal(error) =>
                throw WorkflowException(command, s"Unexpected failure: $error", Some(error))
            } finally {
              context.deactivate() // Very important!
            }

          Future.successful(toProtoEffect(effect, command.id, errorCode))

        case Step(executeStep) =>
          val context =
            new CommandContextImpl(
              workflowId,
              executeStep.stepName,
              executeStep.commandId,
              Metadata.EMPTY,
              // FIXME we'd need to start a parent span for the step here to have one to base custom user spans of off?
              None,
              tracerFactory)
          val timerScheduler =
            new TimerSchedulerImpl(timerClient, context.componentCallMetadata)
          val stepResponse =
            try {
              executeStep.userState.foreach { state =>
                val bytesPayload = AnySupport.toSpiBytesPayload(state)
                val decoded = service.serializer.fromBytes(bytesPayload)
                router._internalSetInitState(decoded, finished = false) // here we know that workflow is still running
              }
              router._internalHandleStep(
                executeStep.commandId,
                executeStep.input,
                executeStep.stepName,
                service.serializer,
                timerScheduler,
                context,
                sdkExcutionContext)
            } catch {
              case e: WorkflowException => throw e
              case NonFatal(ex) =>
                throw WorkflowException(
                  s"unexpected exception [${ex.getMessage}] while executing step [${executeStep.stepName}]",
                  Some(ex))
            }

          stepResponse.map { stp =>
            WorkflowStreamOut(WorkflowStreamOut.Message.Response(stp))
          }

        case Transition(cmd) =>
          val CommandResult(effect) =
            try {
              router._internalGetNextStep(cmd.stepName, cmd.result.get, service.serializer)
            } catch {
              case e: WorkflowException => throw e
              case NonFatal(ex) =>
                throw WorkflowException(
                  s"unexpected exception [${ex.getMessage}] while executing transition for step [${cmd.stepName}]",
                  Some(ex))
            }

          Future.successful(toProtoEffect(effect, cmd.commandId, None))

        case Message.UpdateState(updateState) =>
          updateState.userState match {
            case Some(state) =>
              val bytesPayload = AnySupport.toSpiBytesPayload(state)
              val decoded = service.serializer.fromBytes(bytesPayload)
              router._internalSetInitState(decoded, updateState.finished)
            case None => // no state
          }
          Future.successful(WorkflowStreamOut(WorkflowStreamOut.Message.Empty))

        case Init(_) =>
          throw ProtocolException(init, "Workflow already initiated")

        case Empty =>
          throw ProtocolException(init, "Workflow received empty/unknown message")

        case _ =>
          //dummy case to allow future protocol updates without breaking existing workflows
          Future.successful(WorkflowStreamOut(WorkflowStreamOut.Message.Empty))
      }

    (flow, workflowConfig)
  }

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
