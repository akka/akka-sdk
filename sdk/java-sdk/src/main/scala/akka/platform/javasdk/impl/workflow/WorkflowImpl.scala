/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.impl.workflow

import java.util.Optional

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.jdk.OptionConverters._
import scala.language.existentials
import scala.util.control.NonFatal

import akka.NotUsed
import akka.actor.ActorSystem
import akka.platform.javasdk.JsonSupport
import akka.platform.javasdk.impl.AbstractContext
import akka.platform.javasdk.impl.ActivatableContext
import akka.platform.javasdk.impl.ComponentOptions
import akka.platform.javasdk.impl.ErrorHandling
import akka.platform.javasdk.impl.ErrorHandling.BadRequestException
import akka.platform.javasdk.impl.JsonMessageCodec
import akka.platform.javasdk.impl.MessageCodec
import akka.platform.javasdk.impl.MetadataImpl
import akka.platform.javasdk.impl.ResolvedEntityFactory
import akka.platform.javasdk.impl.ResolvedServiceMethod
import akka.platform.javasdk.impl.Service
import akka.platform.javasdk.impl.StrictJsonMessageCodec
import akka.platform.javasdk.impl.WorkflowExceptions.ProtocolException
import akka.platform.javasdk.impl.WorkflowExceptions.WorkflowException
import akka.platform.javasdk.impl.WorkflowExceptions.failureMessageForLog
import akka.platform.javasdk.impl.WorkflowFactory
import akka.platform.javasdk.impl.timer.TimerSchedulerImpl
import akka.platform.javasdk.impl.workflow.WorkflowEffectImpl.DeleteState
import akka.platform.javasdk.impl.workflow.WorkflowEffectImpl.End
import akka.platform.javasdk.impl.workflow.WorkflowEffectImpl.ErrorEffectImpl
import akka.platform.javasdk.impl.workflow.WorkflowEffectImpl.NoPersistence
import akka.platform.javasdk.impl.workflow.WorkflowEffectImpl.NoReply
import akka.platform.javasdk.impl.workflow.WorkflowEffectImpl.NoTransition
import akka.platform.javasdk.impl.workflow.WorkflowEffectImpl.Pause
import akka.platform.javasdk.impl.workflow.WorkflowEffectImpl.Persistence
import akka.platform.javasdk.impl.workflow.WorkflowEffectImpl.Reply
import akka.platform.javasdk.impl.workflow.WorkflowEffectImpl.ReplyValue
import akka.platform.javasdk.impl.workflow.WorkflowEffectImpl.StepTransition
import akka.platform.javasdk.impl.workflow.WorkflowEffectImpl.TransitionalEffectImpl
import akka.platform.javasdk.impl.workflow.WorkflowEffectImpl.UpdateState
import akka.platform.javasdk.impl.workflow.WorkflowRouter.CommandResult
import akka.platform.javasdk.spi.TimerClient
import akka.platform.javasdk.workflow.Workflow
import akka.platform.javasdk.workflow.Workflow.WorkflowDef
import akka.platform.javasdk.workflow.CommandContext
import akka.platform.javasdk.workflow.WorkflowContext
import akka.platform.javasdk.workflow.WorkflowOptions
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Source
import com.google.protobuf.ByteString
import com.google.protobuf.any.{ Any => ScalaPbAny }
import com.google.protobuf.duration
import com.google.protobuf.duration.Duration
import io.grpc.Status
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
// FIXME these don't seem to be 'public API', more internals?
import scala.jdk.CollectionConverters._

import akka.platform.javasdk.Metadata
import com.google.protobuf.Descriptors

final class WorkflowService(
    val factory: WorkflowFactory,
    override val descriptor: Descriptors.ServiceDescriptor,
    override val additionalDescriptors: Array[Descriptors.FileDescriptor],
    val messageCodec: JsonMessageCodec,
    override val serviceName: String,
    val workflowOptions: Option[WorkflowOptions])
    extends Service {

  def this(
      factory: WorkflowFactory,
      descriptor: Descriptors.ServiceDescriptor,
      additionalDescriptors: Array[Descriptors.FileDescriptor],
      messageCodec: MessageCodec,
      workflowName: String,
      workflowOptions: WorkflowOptions) =
    this(
      factory,
      descriptor,
      additionalDescriptors,
      // FIXME ugh
      messageCodec.asInstanceOf[JsonMessageCodec],
      workflowName,
      Some(workflowOptions))

  val strictMessageCodec = new StrictJsonMessageCodec(new JsonMessageCodec)

  override def resolvedMethods: Option[Map[String, ResolvedServiceMethod[_, _]]] =
    factory match {
      case resolved: ResolvedEntityFactory => Some(resolved.resolvedMethods)
      case _                               => None
    }

  override final val componentType = WorkflowEntities.name

  override def componentOptions: Option[ComponentOptions] = workflowOptions
}

final class WorkflowImpl(
    system: ActorSystem,
    val services: Map[String, WorkflowService],
    timerClient: TimerClient,
    sdkExcutionContext: ExecutionContext,
    sdkDispatcherName: String)
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
          // if error during recovery in proxy the stream will be completed before init
          log.warn("Workflow stream closed before init.")
          Source.empty[WorkflowStreamOut]

        case (Seq(WorkflowStreamIn(other, _)), _) =>
          throw ProtocolException(s"Expected init message for Workflow, but received [${other.getClass.getName}]")
      }
      .recover { case error =>
        ErrorHandling.withCorrelationId { correlationId =>
          log.error(failureMessageForLog(error), error)
          WorkflowStreamOut(OutFailure(component.Failure(description = s"Unexpected error [$correlationId]")))
        }
      }
      .async(sdkDispatcherName)

  private def toRecoverStrategy(messageCodec: MessageCodec)(
      recoverStrategy: Workflow.RecoverStrategy[_]): RecoverStrategy = {
    RecoverStrategy(
      maxRetries = recoverStrategy.maxRetries,
      failoverTo = Some(
        ProtoStepTransition(
          recoverStrategy.failoverStepName,
          recoverStrategy.failoverStepInput.toScala.map(messageCodec.encodeScala))))
  }

  private def toStepConfig(
      name: String,
      timeout: Optional[java.time.Duration],
      recoverStrategy: Option[Workflow.RecoverStrategy[_]],
      messageCodec: MessageCodec) = {
    val stepTimeout = timeout.toScala.map(duration.Duration(_))
    val stepRecoverStrategy = recoverStrategy.map(toRecoverStrategy(messageCodec))
    StepConfig(name, stepTimeout, stepRecoverStrategy)
  }

  private def toWorkflowConfig(workflowDefinition: WorkflowDef[_], messageCodec: MessageCodec): WorkflowConfig = {
    val workflowTimeout = workflowDefinition.getWorkflowTimeout.toScala.map(Duration(_))
    val stepConfigs = workflowDefinition.getStepConfigs.asScala
      .map(c => toStepConfig(c.stepName, c.timeout, c.recoverStrategy.toScala, messageCodec))
      .toSeq
    val stepConfig =
      toStepConfig(
        "",
        workflowDefinition.getStepTimeout,
        workflowDefinition.getStepRecoverStrategy.toScala,
        messageCodec)

    val failoverTo = workflowDefinition.getFailoverStepName.toScala.map(stepName => {
      ProtoStepTransition(stepName, workflowDefinition.getFailoverStepInput.toScala.map(messageCodec.encodeScala))
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
      service.factory.create(new WorkflowContextImpl(init.entityId))
    val workflowId = init.entityId

    val workflowConfig =
      WorkflowStreamOut(
        WorkflowStreamOut.Message.Config(toWorkflowConfig(router._getWorkflowDefinition(), service.strictMessageCodec)))

    init.userState match {
      case Some(state) =>
        val decoded = service.strictMessageCodec.decodeMessage(state)
        router._internalSetInitState(decoded, init.finished)
      case None => // no initial state
    }

    def toProtoEffect(effect: Workflow.Effect[_], commandId: Long, errorCode: Option[Status.Code]) = {

      def effectMessage[R](persistence: Persistence[_], transition: WorkflowEffectImpl.Transition, reply: Reply[R]) = {

        val protoEffect =
          persistence match {
            case UpdateState(newState) =>
              router._internalSetInitState(newState, transition.isInstanceOf[End.type])
              WorkflowEffect.defaultInstance.withUserState(service.strictMessageCodec.encodeScala(newState))
            // TODO: persistence should be optional, but we must ensure that we don't save it back to null
            // and preferably we should not even send it over the wire.
            case NoPersistence => WorkflowEffect.defaultInstance
            case DeleteState   => throw new RuntimeException("Workflow state deleted not yet supported")
          }

        val toProtoTransition =
          transition match {
            case StepTransition(stepName, input) =>
              WorkflowEffect.Transition.StepTransition(
                ProtoStepTransition(stepName, input.map(service.strictMessageCodec.encodeScala)))
            case Pause        => WorkflowEffect.Transition.Pause(ProtoPause.defaultInstance)
            case NoTransition => WorkflowEffect.Transition.NoTransition(ProtoNoTransition.defaultInstance)
            case End          => WorkflowEffect.Transition.EndTransition(ProtoEndTransition.defaultInstance)
          }

        val clientAction = {
          val protoReply =
            reply match {
              case ReplyValue(value, metadata) =>
                ProtoReply(
                  payload = Some(service.strictMessageCodec.encodeScala(value)),
                  metadata = MetadataImpl.toProtocol(metadata))
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

          val context = new CommandContextImpl(workflowId, command.name, command.id, metadata, system)
          val timerScheduler =
            new TimerSchedulerImpl(service.strictMessageCodec, timerClient, context.componentCallMetadata)

          val cmd =
            service.messageCodec.decodeMessage(
              command.payload.getOrElse(
                // FIXME smuggling 0 arity method called from component client through here
                ScalaPbAny.defaultInstance.withTypeUrl(JsonSupport.KALIX_JSON).withValue(ByteString.empty())))

          val (CommandResult(effect), errorCode) =
            try {
              (router._internalHandleCommand(command.name, cmd, context, timerScheduler), None)
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
            new CommandContextImpl(workflowId, executeStep.stepName, executeStep.commandId, Metadata.EMPTY, system)
          val timerScheduler =
            new TimerSchedulerImpl(service.strictMessageCodec, timerClient, context.componentCallMetadata)
          val stepResponse =
            try {
              executeStep.userState.foreach { state =>
                val decoded = service.strictMessageCodec.decodeMessage(state)
                router._internalSetInitState(decoded, finished = false) // here we know that workflow is still running
              }
              router._internalHandleStep(
                executeStep.commandId,
                executeStep.input,
                executeStep.stepName,
                service.strictMessageCodec,
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
              router._internalGetNextStep(cmd.stepName, cmd.result.get, service.strictMessageCodec)
            } catch {
              case e: WorkflowException => throw e
              case NonFatal(ex) =>
                throw WorkflowException(
                  s"unexpected exception [${ex.getMessage}] while executing transition for step [${cmd.stepName}]",
                  Some(ex))
            }

          Future.successful(toProtoEffect(effect, cmd.commandId, None))

        case Init(_) =>
          throw ProtocolException(init, "Workflow already initiated")

        case Empty =>
          throw ProtocolException(init, "Workflow received empty/unknown message")
      }

    (flow, workflowConfig)
  }

}

private[akka] final class CommandContextImpl(
    override val workflowId: String,
    override val commandName: String,
    override val commandId: Long,
    override val metadata: Metadata,
    system: ActorSystem)
    extends AbstractContext
    with CommandContext
    with ActivatableContext

private[akka] final class WorkflowContextImpl(override val workflowId: String)
    extends AbstractContext
    with WorkflowContext
