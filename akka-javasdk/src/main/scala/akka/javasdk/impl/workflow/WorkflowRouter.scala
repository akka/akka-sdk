/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.workflow

import java.util.Optional
import java.util.concurrent.CompletionStage
import java.util.function.{ Function => JFunc }

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.jdk.FutureConverters.CompletionStageOps
import scala.jdk.OptionConverters.RichOptional

import akka.annotation.InternalApi
import akka.javasdk.impl.AnySupport
import akka.javasdk.impl.WorkflowExceptions.WorkflowException
import akka.javasdk.impl.serialization.JsonSerializer
import akka.javasdk.impl.workflow.WorkflowRouter.CommandHandlerNotFound
import akka.javasdk.impl.workflow.WorkflowRouter.CommandResult
import akka.javasdk.impl.workflow.WorkflowRouter.WorkflowStepNotFound
import akka.javasdk.impl.workflow.WorkflowRouter.WorkflowStepNotSupported
import akka.javasdk.timer.TimerScheduler
import akka.javasdk.workflow.CommandContext
import akka.javasdk.workflow.Workflow
import akka.javasdk.workflow.Workflow.AsyncCallStep
import akka.javasdk.workflow.Workflow.Effect
import akka.runtime.sdk.spi.BytesPayload

/**
 * INTERNAL API
 */
@InternalApi
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

/**
 * INTERNAL API
 */
@InternalApi
abstract class WorkflowRouter[S, W <: Workflow[S]](protected val workflow: W) {

  private var state: Option[S] = None
  private var workflowFinished: Boolean = false

  private def stateOrEmpty(): S = state match {
    case None =>
      val emptyState = workflow.emptyState()
      // null is allowed as emptyState
      state = Some(emptyState)
      emptyState
    case Some(state) =>
      state
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
      command: BytesPayload,
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
            commandName,
            s"No command handler found for command [$name] on ${workflow.getClass}")
      } finally {
        workflow._internalSetCommandContext(Optional.empty())
      }

    CommandResult(commandEffect)
  }

  protected def handleCommand(
      commandName: String,
      state: S,
      command: BytesPayload,
      context: CommandContext): Workflow.Effect[_]

  // in same cases, the runtime may send a message with typeUrl set to object.
  // if that's the case, we need to patch the message using the typeUrl from the expected input class
  private def decodeInput(serializer: JsonSerializer, result: BytesPayload, expectedInputClass: Class[_]) = {
    if ((serializer.isJson(result) &&
      result.contentType.endsWith("/object")) ||
      result.contentType == AnySupport.JsonTypeUrlPrefix) {
      serializer.fromBytes(expectedInputClass, result)
    } else {
      serializer.fromBytes(result)
    }
  }

  /** INTERNAL API */
  // "public" api against the impl/testkit
  final def _internalHandleStep(
      input: Option[BytesPayload],
      stepName: String,
      serializer: JsonSerializer,
      timerScheduler: TimerScheduler,
      commandContext: CommandContext,
      executionContext: ExecutionContext): Future[BytesPayload] = {

    implicit val ec: ExecutionContext = executionContext

    workflow._internalSetCurrentState(stateOrEmpty())
    workflow._internalSetTimerScheduler(Optional.of(timerScheduler))
    workflow._internalSetCommandContext(Optional.of(commandContext))
    val workflowDef = workflow.definition()

    workflowDef.findByName(stepName).toScala match {
      case Some(call: AsyncCallStep[_, _, _]) =>
        val decodedInput = input match {
          case Some(inputValue) => decodeInput(serializer, inputValue, call.callInputClass)
          case None             => null // to meet a signature of supplier expressed as a function
        }

        val future = call.callFunc
          .asInstanceOf[JFunc[Any, CompletionStage[Any]]]
          .apply(decodedInput)
          .asScala

        future.map(serializer.toBytes)

      case Some(any) => Future.failed(WorkflowStepNotSupported(any.getClass.getSimpleName))
      case None      => Future.failed(WorkflowStepNotFound(stepName))
    }

  }

  def _internalGetNextStep(stepName: String, result: BytesPayload, serializer: JsonSerializer): CommandResult = {

    workflow._internalSetCurrentState(stateOrEmpty())
    val workflowDef = workflow.definition()

    workflowDef.findByName(stepName).toScala match {
      case Some(call: AsyncCallStep[_, _, _]) =>
        val effect =
          call.transitionFunc
            .asInstanceOf[JFunc[Any, Effect[Any]]]
            .apply(decodeInput(serializer, result, call.transitionInputClass))

        CommandResult(effect)

      case Some(any) => throw WorkflowStepNotSupported(any.getClass.getSimpleName)
      case None      => throw WorkflowStepNotFound(stepName)
    }
  }
}
