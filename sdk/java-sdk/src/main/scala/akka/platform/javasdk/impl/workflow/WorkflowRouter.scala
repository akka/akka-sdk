/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.impl.workflow

import akka.http.scaladsl.model.StatusCode

import java.nio.ByteBuffer
import java.util.Optional
import java.util.concurrent.CompletionStage
import java.util.function.{ Function => JFunc }
import scala.compat.java8.FutureConverters.CompletionStageOps
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters.RichOptional
import com.google.api.HttpBody
import com.google.protobuf.any.{ Any => ScalaPbAny }
import akka.platform.javasdk.HttpResponse
import akka.platform.javasdk.HttpResponse.STATUS_CODE_EXTENSION_TYPE_URL
import akka.platform.javasdk.JsonSupport
import akka.platform.javasdk.impl.MessageCodec
import akka.platform.javasdk.impl.WorkflowExceptions.WorkflowException
import akka.platform.javasdk.impl.workflow.WorkflowRouter.CommandHandlerNotFound
import akka.platform.javasdk.impl.workflow.WorkflowRouter.CommandResult
import akka.platform.javasdk.impl.workflow.WorkflowRouter.WorkflowStepNotFound
import akka.platform.javasdk.impl.workflow.WorkflowRouter.WorkflowStepNotSupported
import akka.platform.javasdk.timer.TimerScheduler
import akka.platform.javasdk.workflow.Workflow
import akka.platform.javasdk.workflow.Workflow.AsyncCallStep
import akka.platform.javasdk.workflow.Workflow.CallStep
import akka.platform.javasdk.workflow.Workflow.Effect
import akka.platform.javasdk.workflow.Workflow.WorkflowDef
import akka.platform.javasdk.workflow.CommandContext
import kalix.protocol.workflow_entity.StepExecuted
import kalix.protocol.workflow_entity.StepExecutionFailed
import kalix.protocol.workflow_entity.StepResponse
import org.slf4j.LoggerFactory

object WorkflowRouter {
  final case class CommandResult(effect: Workflow.Effect[_])

  final case class CommandHandlerNotFound(commandName: String) extends RuntimeException {
    override def getMessage: String = commandName
  }
  final case class WorkflowStepNotFound(stepName: String) extends RuntimeException {
    override def getMessage: String = stepName
  }

  final case class WorkflowStepNotSupported(stepName: String) extends RuntimeException {
    override def getMessage: String = stepName
  }
}

abstract class WorkflowRouter[S, W <: Workflow[S]](protected val workflow: W) {

  private var state: Option[S] = None
  private var workflowFinished: Boolean = false
  private final val log = LoggerFactory.getLogger(this.getClass)

  private def stateOrEmpty(): S = state match {
    case None =>
      val emptyState = workflow.emptyState()
      // null is allowed as emptyState
      state = Some(emptyState)
      emptyState
    case Some(state) =>
      state
  }

  def _getWorkflowDefinition(): WorkflowDef[S] = {
    workflow.definition()
  }

  /** INTERNAL API */
  // "public" api against the impl/testkit
  def _internalSetInitState(s: Any, finished: Boolean): Unit = {
    if (!workflowFinished) {
      state = Some(s.asInstanceOf[S])
      workflowFinished = finished
    }
  }

  /** INTERNAL API */
  // "public" api against the impl/testkit
  final def _internalHandleCommand(
      commandName: String,
      command: Any,
      context: CommandContext,
      timerScheduler: TimerScheduler): CommandResult = {
    val commandEffect =
      try {
        workflow._internalSetTimerScheduler(Optional.of(timerScheduler))
        workflow._internalSetCommandContext(Optional.of(context))
        workflow._internalSetCurrentState(stateOrEmpty())
        handleCommand(commandName, stateOrEmpty(), command, context).asInstanceOf[Effect[Any]]
      } catch {
        case CommandHandlerNotFound(name) =>
          throw new WorkflowException(
            context.workflowId(),
            context.commandId(),
            commandName,
            s"No command handler found for command [$name] on ${workflow.getClass}")
      } finally {
        workflow._internalSetCommandContext(Optional.empty())
      }

    CommandResult(commandEffect)
  }

  protected def handleCommand(commandName: String, state: S, command: Any, context: CommandContext): Workflow.Effect[_]

  // in same cases, the Proxy may send a message with typeUrl set to object.
  // if that's the case, we need to patch the message using the typeUrl from the expected input class
  private def decodeInput(messageCodec: MessageCodec, result: ScalaPbAny, expectedInputClass: Class[_]) = {
    if (result.typeUrl == JsonSupport.KALIX_JSON + "object" || result.typeUrl == JsonSupport.KALIX_JSON) {
      JsonSupport.decodeJson(expectedInputClass, result)
    } else if (result.typeUrl == "type.googleapis.com/google.api.HttpBody") {
      val httpBody = HttpBody.parseFrom(result.value.newCodedInput())
      //HttpBodyStatusCodeExtensionTypeUrl constant from java-sdk-spring
      httpBody.getExtensionsList.asScala.find(_.getTypeUrl == STATUS_CODE_EXTENSION_TYPE_URL) match {
        case Some(statusCodeAny) =>
          val statusCode = StatusCode.int2StatusCode(ByteBuffer.wrap(statusCodeAny.getValue.toByteArray).getInt)
          val contentType = httpBody.getContentType
          HttpResponse.of(statusCode, contentType, httpBody.getData.toByteArray)
        case None => throw new IllegalStateException("Missing status code extension in HttpBody")
      }
    } else {
      messageCodec.decodeMessage(result)
    }
  }

  /** INTERNAL API */
  // "public" api against the impl/testkit
  final def _internalHandleStep(
      commandId: Long,
      input: Option[ScalaPbAny],
      stepName: String,
      messageCodec: MessageCodec,
      timerScheduler: TimerScheduler,
      commandContext: CommandContext,
      executionContext: ExecutionContext): Future[StepResponse] = {

    implicit val ec = executionContext

    workflow._internalSetCurrentState(stateOrEmpty())
    workflow._internalSetTimerScheduler(Optional.of(timerScheduler))
    workflow._internalSetCommandContext(Optional.of(commandContext))
    val workflowDef = workflow.definition()

    workflowDef.findByName(stepName).toScala match {
      case Some(call: CallStep[_, _, _, _]) =>
        throw new IllegalStateException(s"DeferredCall not supported for workflows: [$call]")

      case Some(call: AsyncCallStep[_, _, _]) =>
        val decodedInput = input match {
          case Some(inputValue) => decodeInput(messageCodec, inputValue, call.callInputClass)
          case None             => null // to meet a signature of supplier expressed as a function
        }

        val future = call.callFunc
          .asInstanceOf[JFunc[Any, CompletionStage[Any]]]
          .apply(decodedInput)
          .toScala

        future
          .map { res =>
            val encoded = messageCodec.encodeScala(res)
            val executedRes = StepExecuted(Some(encoded))

            StepResponse(commandId, stepName, StepResponse.Response.Executed(executedRes))
          }
          .recover { case t: Throwable =>
            log.error("Workflow async call failed.", t)
            StepResponse(commandId, stepName, StepResponse.Response.ExecutionFailed(StepExecutionFailed(t.getMessage)))
          }
      case Some(any) => Future.failed(WorkflowStepNotSupported(any.getClass.getSimpleName))
      case None      => Future.failed(WorkflowStepNotFound(stepName))
    }

  }

  def _internalGetNextStep(stepName: String, result: ScalaPbAny, messageCodec: MessageCodec): CommandResult = {

    workflow._internalSetCurrentState(stateOrEmpty())
    val workflowDef = workflow.definition()

    workflowDef.findByName(stepName).toScala match {
      case Some(call: CallStep[_, _, _, _]) =>
        val effect =
          call.transitionFunc
            .asInstanceOf[JFunc[Any, Effect[Any]]]
            .apply(decodeInput(messageCodec, result, call.transitionInputClass))

        CommandResult(effect)

      case Some(call: AsyncCallStep[_, _, _]) =>
        val effect =
          call.transitionFunc
            .asInstanceOf[JFunc[Any, Effect[Any]]]
            .apply(decodeInput(messageCodec, result, call.transitionInputClass))

        CommandResult(effect)

      case Some(any) => throw WorkflowStepNotSupported(any.getClass.getSimpleName)
      case None      => throw WorkflowStepNotFound(stepName)
    }
  }
}
