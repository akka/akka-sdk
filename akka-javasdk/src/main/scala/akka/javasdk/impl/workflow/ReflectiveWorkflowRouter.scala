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
import akka.javasdk.impl.MethodInvoker
import akka.javasdk.impl.CommandSerialization
import akka.javasdk.impl.HandlerNotFoundException
import akka.javasdk.impl.serialization.JsonSerializer
import akka.javasdk.impl.workflow.ReflectiveWorkflowRouter.CommandResult
import akka.javasdk.impl.workflow.ReflectiveWorkflowRouter.TransitionalResult
import akka.javasdk.impl.workflow.ReflectiveWorkflowRouter.WorkflowStepNotFound
import akka.javasdk.impl.workflow.ReflectiveWorkflowRouter.WorkflowStepNotSupported
import akka.javasdk.timer.TimerScheduler
import akka.javasdk.workflow.CommandContext
import akka.javasdk.workflow.Workflow
import akka.javasdk.workflow.Workflow.{ AsyncCallStep, CallStep, RunnableStep }
import akka.javasdk.workflow.Workflow.Effect.TransitionalEffect
import akka.javasdk.workflow.WorkflowContext
import akka.runtime.sdk.spi.BytesPayload
import akka.runtime.sdk.spi.SpiWorkflow

/**
 * INTERNAL API
 */
@InternalApi
object ReflectiveWorkflowRouter {
  final case class CommandResult(effect: Workflow.Effect[_])

  final case class TransitionalResult(effect: Workflow.Effect.TransitionalEffect[_])
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
    workflowContext: WorkflowContext,
    instanceFactory: Function[WorkflowContext, W],
    methodInvokers: Map[String, MethodInvoker],
    serializer: JsonSerializer) {

  private def decodeUserState(userState: Option[BytesPayload]): Option[S] =
    userState
      .collect {
        case payload if payload.nonEmpty => serializer.fromBytes(payload).asInstanceOf[S]
      }

  // in same cases, the runtime may send a message with contentType set to object.
  // if that's the case, we need to patch the message using the contentType from the expected input class
  private def decodeInput(result: BytesPayload, expectedInputClass: Class[_]) = {
    if (result.isEmpty) null // input can't be empty, but just in case
    else if (serializer.isJson(result) &&
      result.contentType.endsWith("/object")) {
      serializer.fromBytes(expectedInputClass, result)
    } else {
      serializer.fromBytes(result)
    }
  }

  private def methodInvokerLookup(commandName: String) =
    methodInvokers.getOrElse(
      commandName,
      throw new HandlerNotFoundException(
        "command",
        commandName,
        instanceFactory(workflowContext).getClass,
        methodInvokers.keySet))

  final def handleCommand(
      userState: Option[SpiWorkflow.State],
      commandName: String,
      command: BytesPayload,
      context: CommandContext,
      timerScheduler: TimerScheduler): CommandResult = {

    val workflow = instanceFactory(workflowContext)

    // if runtime doesn't have a state to provide, we fall back to user's own defined empty state
    val decodedState = decodeUserState(userState).getOrElse(workflow.emptyState())
    workflow._internalSetup(decodedState, context, timerScheduler)

    val methodInvoker = methodInvokerLookup(commandName)

    if (serializer.isJson(command) || command.isEmpty) {
      // - BytesPayload.empty - there is no real command, and we are calling a method with arity 0
      // - BytesPayload with json - we deserialize it and call the method
      val deserializedCommand =
        CommandSerialization.deserializeComponentClientCommand(methodInvoker.method, command, serializer)
      val result = deserializedCommand match {
        case None        => methodInvoker.invoke(workflow)
        case Some(input) => methodInvoker.invokeDirectly(workflow, input)
      }
      CommandResult(result.asInstanceOf[Workflow.Effect[_]])
    } else {
      throw new IllegalStateException(
        s"Could not find a matching command handler for method [$commandName], content type [${command.contentType}] " +
        s"on [${workflow.getClass.getName}]")
    }
  }

  final def handleStep(
      userState: Option[SpiWorkflow.State],
      input: Option[BytesPayload],
      stepName: String,
      timerScheduler: TimerScheduler,
      commandContext: CommandContext,
      executionContext: ExecutionContext): Future[BytesPayload] = {

    implicit val ec: ExecutionContext = executionContext

    val workflow = instanceFactory(workflowContext)
    // if runtime doesn't have a state to provide, we fall back to user's own defined empty state
    val decodedState = decodeUserState(userState).getOrElse(workflow.emptyState())
    workflow._internalSetup(decodedState, commandContext, timerScheduler)

    def decodeInputForClass(inputClass: Class[_]): Any = input match {
      case Some(inputValue) => decodeInput(inputValue, inputClass)
      case None             => null // to meet a signature of supplier expressed as a function
    }

    workflow.definition().findByName(stepName).toScala match {

      case Some(call: RunnableStep) =>
        call.runnable.run()
        Future.successful(BytesPayload.empty)

      case Some(call: CallStep[_, _, _]) =>
        val decodedInput = decodeInputForClass(call.callInputClass)
        val output = call.callFunc
          .asInstanceOf[JFunc[Any, Any]]
          .apply(decodedInput)

        Future.successful(serializer.toBytes(output))

      case Some(call: AsyncCallStep[_, _, _]) =>
        val decodedInput = decodeInputForClass(call.callInputClass)

        val future = call.callFunc
          .asInstanceOf[JFunc[Any, CompletionStage[Any]]]
          .apply(decodedInput)
          .asScala

        future.map(serializer.toBytes)

      case Some(any) => Future.failed(WorkflowStepNotSupported(any.getClass.getSimpleName))
      case None      => Future.failed(WorkflowStepNotFound(stepName))
    }
  }

  final def getNextStep(stepName: String, result: BytesPayload, userState: Option[BytesPayload]): TransitionalResult = {

    val workflow = instanceFactory(workflowContext)

    // if runtime doesn't have a state to provide, we fall back to user's own defined empty state
    val decodedState = decodeUserState(userState).getOrElse(workflow.emptyState())
    workflow._internalSetup(decodedState)

    def applyTransitionFunc(transitionFunc: JFunc[_, _], transitionInputClass: Class[_]) =
      transitionFunc
        .asInstanceOf[JFunc[Any, TransitionalEffect[Any]]]
        .apply(decodeInput(result, transitionInputClass))

    workflow.definition().findByName(stepName).toScala match {
      case Some(runnableStep: RunnableStep) =>
        val effect = runnableStep.transitionFunc.get()
        TransitionalResult(effect)

      case Some(call: CallStep[_, _, _]) =>
        val effect = applyTransitionFunc(call.transitionFunc, call.transitionInputClass)
        TransitionalResult(effect)

      case Some(call: AsyncCallStep[_, _, _]) =>
        val effect = applyTransitionFunc(call.transitionFunc, call.transitionInputClass)
        TransitionalResult(effect)

      case Some(any) => throw WorkflowStepNotSupported(any.getClass.getSimpleName)
      case None      => throw WorkflowStepNotFound(stepName)
    }
  }
}
