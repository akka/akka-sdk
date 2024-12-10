/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.workflow

import java.util.concurrent.CompletionStage
import java.util.function.{ Function => JFunc }

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.jdk.FutureConverters.CompletionStageOps
import scala.jdk.OptionConverters.RichOptional

import akka.annotation.InternalApi
import akka.javasdk.impl.AnySupport
import akka.javasdk.impl.CommandHandler
import akka.javasdk.impl.CommandSerialization
import akka.javasdk.impl.InvocationContext
import akka.javasdk.impl.WorkflowExceptions.WorkflowException
import akka.javasdk.impl.serialization.JsonSerializer
import akka.javasdk.impl.workflow.ReflectiveWorkflowRouter.CommandHandlerNotFound
import akka.javasdk.impl.workflow.ReflectiveWorkflowRouter.CommandResult
import akka.javasdk.impl.workflow.ReflectiveWorkflowRouter.WorkflowStepNotFound
import akka.javasdk.impl.workflow.ReflectiveWorkflowRouter.WorkflowStepNotSupported
import akka.javasdk.timer.TimerScheduler
import akka.javasdk.workflow.CommandContext
import akka.javasdk.workflow.Workflow
import akka.javasdk.workflow.Workflow.AsyncCallStep
import akka.javasdk.workflow.Workflow.Effect
import akka.runtime.sdk.spi.BytesPayload
import akka.runtime.sdk.spi.SpiWorkflow

/**
 * INTERNAL API
 */
@InternalApi
object ReflectiveWorkflowRouter {
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
class ReflectiveWorkflowRouter[S, W <: Workflow[S]](
    val workflow: W,
    commandHandlers: Map[String, CommandHandler],
    serializer: JsonSerializer) {

  private def decodeUserState(userState: Option[BytesPayload]): S =
    userState
      .collect {
        case payload if payload != BytesPayload.empty => serializer.fromBytes(payload).asInstanceOf[S]
      }
      // if runtime doesn't have a state to provide, we fall back to user's own defined empty state
      .getOrElse(workflow.emptyState())

  // in same cases, the runtime may send a message with contentType set to object.
  // if that's the case, we need to patch the message using the contentType from the expected input class
  private def decodeInput(result: BytesPayload, expectedInputClass: Class[_]) = {
    if (result == BytesPayload.empty) null // input can't be empty, but just in case
    else if ((serializer.isJson(result) &&
      result.contentType.endsWith("/object")) ||
      result.contentType == AnySupport.JsonTypeUrlPrefix) {
      serializer.fromBytes(expectedInputClass, result)
    } else {
      serializer.fromBytes(result)
    }
  }

  private def commandHandlerLookup(commandName: String) =
    commandHandlers.getOrElse(
      commandName,
      throw new HandlerNotFoundException("command", commandName, commandHandlers.keySet))

  /** INTERNAL API */
  // "public" api against the impl/testkit
  final def handleCommand(
      userState: Option[SpiWorkflow.State],
      commandName: String,
      command: BytesPayload,
      context: CommandContext,
      timerScheduler: TimerScheduler): CommandResult = {

    val commandEffect =
      try {
        workflow._internalSetup(decodeUserState(userState), context, timerScheduler)

        val commandHandler = commandHandlerLookup(commandName)

        // Commands can be in three shapes:
        // - BytesPayload.empty - there is no real command, and we are calling a method with arity 0
        // - BytesPayload with json - we deserialize it and call the method
        // - BytesPayload with Proto encoding - we deserialize using InvocationContext
        if (serializer.isJson(command) || command != BytesPayload.empty) {
          // special cased component client calls, lets json commands through all the way
          val methodInvoker = commandHandler.getSingleNameInvoker()
          val deserializedCommand =
            CommandSerialization.deserializeComponentClientCommand(methodInvoker.method, command, serializer)
          val result = deserializedCommand match {
            case None        => methodInvoker.invoke(workflow)
            case Some(input) => methodInvoker.invokeDirectly(workflow, input)
          }
          result.asInstanceOf[Workflow.Effect[_]]
        } else {

          // FIXME can be proto from http-grpc-handling of the static es endpoints
          val pbAnyCommand = AnySupport.toScalaPbAny(command)
          val invocationContext =
            InvocationContext(pbAnyCommand, commandHandler.requestMessageDescriptor, context.metadata())

          val inputTypeUrl = pbAnyCommand.typeUrl

          val methodInvoker = commandHandler.getInvoker(inputTypeUrl)
          methodInvoker
            .invoke(workflow, invocationContext)
            .asInstanceOf[Workflow.Effect[_]]
        }

      } catch {
        case CommandHandlerNotFound(name) =>
          throw new WorkflowException(
            context.workflowId(),
            commandName,
            s"No command handler found for command [$name] on ${workflow.getClass}")
      } finally {
        workflow._internalClear();
      }

    CommandResult(commandEffect)
  }

  /** INTERNAL API */
  // "public" api against the impl/testkit
  final def handleStep(
      userState: Option[SpiWorkflow.State],
      input: Option[BytesPayload],
      stepName: String,
      timerScheduler: TimerScheduler,
      commandContext: CommandContext,
      executionContext: ExecutionContext): Future[BytesPayload] = {

    implicit val ec: ExecutionContext = executionContext

    try {
      workflow._internalSetup(decodeUserState(userState), commandContext, timerScheduler)
      workflow.definition().findByName(stepName).toScala match {
        case Some(call: AsyncCallStep[_, _, _]) =>
          val decodedInput = input match {
            case Some(inputValue) => decodeInput(inputValue, call.callInputClass)
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
    } finally {
      workflow._internalClear()
    }

  }

  final def getNextStep(stepName: String, result: BytesPayload, userState: Option[BytesPayload]): CommandResult = {

    try {
      workflow._internalSetup(decodeUserState(userState))
      workflow.definition().findByName(stepName).toScala match {
        case Some(call: AsyncCallStep[_, _, _]) =>
          val effect =
            call.transitionFunc
              .asInstanceOf[JFunc[Any, Effect[Any]]]
              .apply(decodeInput(result, call.transitionInputClass))

          CommandResult(effect)

        case Some(any) => throw WorkflowStepNotSupported(any.getClass.getSimpleName)
        case None      => throw WorkflowStepNotFound(stepName)
      }
    } finally {
      workflow._internalClear();
    }
  }
}

/**
 * INTERNAL API
 */
@InternalApi
final class HandlerNotFoundException(handlerType: String, name: String, availableHandlers: Set[String])
    extends RuntimeException(
      s"no matching $handlerType handler for '$name'. " +
      s"Available handlers are: [${availableHandlers.mkString(", ")}]")
