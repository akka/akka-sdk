/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.workflow

import akka.annotation.InternalApi
import akka.javasdk.impl.CommandSerialization
import akka.javasdk.impl.HandlerNotFoundException
import akka.javasdk.impl.MetadataImpl
import akka.javasdk.impl.MethodInvoker
import akka.javasdk.impl.serialization.JsonSerializer
import akka.javasdk.impl.workflow.ReflectiveWorkflowRouter.WorkflowStepNotFound
import akka.javasdk.impl.workflow.ReflectiveWorkflowRouter.WorkflowStepNotSupported
import akka.javasdk.impl.workflow.WorkflowEffects.Delete
import akka.javasdk.impl.workflow.WorkflowEffects.End
import akka.javasdk.impl.workflow.WorkflowEffects.NoPersistence
import akka.javasdk.impl.workflow.WorkflowEffects.NoTransition
import akka.javasdk.impl.workflow.WorkflowEffects.Pause
import akka.javasdk.impl.workflow.WorkflowEffects.Persistence
import akka.javasdk.impl.workflow.WorkflowEffects.StepTransition
import akka.javasdk.impl.workflow.WorkflowEffects.Transition
import akka.javasdk.impl.workflow.WorkflowEffects.UpdateState
import akka.javasdk.impl.workflow.WorkflowEffects.WorkflowEffectImpl
import akka.javasdk.impl.workflow.WorkflowEffects.WorkflowEffectImpl.ErrorEffectImpl
import akka.javasdk.impl.workflow.WorkflowEffects.WorkflowEffectImpl.NoReply
import akka.javasdk.impl.workflow.WorkflowEffects.WorkflowEffectImpl.ReplyValue
import akka.javasdk.impl.workflow.WorkflowEffects.WorkflowEffectImpl.TransitionalEffectImpl
import akka.javasdk.impl.workflow.WorkflowEffects.WorkflowStepEffectImpl
import akka.javasdk.timer.TimerScheduler
import akka.javasdk.workflow.CommandContext
import akka.javasdk.workflow.Workflow
import akka.javasdk.workflow.Workflow.AsyncCallStep
import akka.javasdk.workflow.Workflow.CallStep
import akka.javasdk.workflow.Workflow.Effect.TransitionalEffect
import akka.javasdk.workflow.Workflow.RunnableStep
import akka.javasdk.workflow.WorkflowContext
import akka.runtime.sdk.spi.BytesPayload
import akka.runtime.sdk.spi.SpiEntity
import akka.runtime.sdk.spi.SpiMetadata
import akka.runtime.sdk.spi.SpiWorkflow
import akka.runtime.sdk.spi.SpiWorkflow.StepCallReply

import java.util.concurrent.CompletionStage
import java.util.function.{ Function => JFunc }
import scala.annotation.nowarn
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.jdk.FutureConverters.CompletionStageOps

/**
 * INTERNAL API
 */
@InternalApi
object ReflectiveWorkflowRouter {

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

  private def methodInvokerLookup(commandName: String, workflowClass: Class[_]) =
    methodInvokers.getOrElse(
      commandName,
      throw new HandlerNotFoundException("command", commandName, workflowClass, methodInvokers.keySet))

  final def handleCommand(
      userState: Option[SpiWorkflow.State],
      commandName: String,
      command: BytesPayload,
      context: CommandContext,
      timerScheduler: TimerScheduler,
      deleted: Boolean,
      workflowContext: WorkflowContext): SpiWorkflow.CommandEffect = {

    val workflow = instanceFactory(workflowContext)

    // if runtime doesn't have a state to provide, we fall back to user's own defined empty state
    val decodedState = decodeUserState(userState).getOrElse(workflow.emptyState())
    workflow._internalSetup(decodedState, context, timerScheduler, deleted)

    val methodInvoker = methodInvokerLookup(commandName, workflow.getClass)

    if (serializer.isJson(command) || command.isEmpty) {
      // - BytesPayload.empty - there is no real command, and we are calling a method with arity 0
      // - BytesPayload with json - we deserialize it and call the method
      val deserializedCommand =
        CommandSerialization.deserializeComponentClientCommand(methodInvoker.method, command, serializer)
      val result = deserializedCommand match {
        case None        => methodInvoker.invoke(workflow)
        case Some(input) => methodInvoker.invokeDirectly(workflow, input)
      }
      toSpiCommandEffect(result.asInstanceOf[Workflow.Effect[_]])
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
      executionContext: ExecutionContext,
      workflowContext: WorkflowContext): Future[SpiWorkflow.StepResult] = {

    implicit val ec: ExecutionContext = executionContext

    val workflow = instanceFactory(workflowContext)
    // if runtime doesn't have a state to provide, we fall back to user's own defined empty state
    val decodedState = decodeUserState(userState).getOrElse(workflow.emptyState())
    workflow._internalSetup(decodedState, commandContext, timerScheduler, false)

    def decodeInputForClass(inputClass: Class[_]): Any = input match {
      case Some(inputValue) => decodeInput(inputValue, inputClass)
      case None             => null // to meet a signature of a supplier expressed as a function
    }

    val descriptor = new WorkflowDescriptor(workflow)

    // legacy call step
    @nowarn("msg=deprecated")
    def tryCallStep(stepName: String): Future[SpiWorkflow.StepResult] = {
      descriptor.findStepByName(stepName) match {

        case Some(call: RunnableStep) =>
          Future { // sdkExecutionContext
            call.runnable.run()
            new StepCallReply(BytesPayload.empty)
          }

        case Some(call: CallStep[_, _, _]) =>
          val decodedInput = decodeInputForClass(call.callInputClass)
          Future { // sdkExecutionContext
            val output = call.callFunc
              .asInstanceOf[JFunc[Any, Any]]
              .apply(decodedInput)

            new StepCallReply(serializer.toBytes(output))
          }

        case Some(call: AsyncCallStep[_, _, _]) =>
          val decodedInput = decodeInputForClass(call.callInputClass)

          val future = call.callFunc
            .asInstanceOf[JFunc[Any, CompletionStage[Any]]]
            .apply(decodedInput)
            .asScala

          future.map(any => new StepCallReply(serializer.toBytes(any)))

        case Some(any) => Future.failed(WorkflowStepNotSupported(any.getClass.getSimpleName))
        case None      => Future.failed(WorkflowStepNotFound(stepName))
      }
    }

    descriptor
      .findStepMethodByName(stepName)
      .map { stepMethod =>
        val effect =
          if (stepMethod.javaMethod().getParameterCount == 1) {
            Future {
              val decodedInput = decodeInputForClass(stepMethod.javaMethod().getParameterTypes()(0))
              stepMethod.invoke(workflow, decodedInput)
            }
          } else {
            Future {
              stepMethod.invoke(workflow)
            }
          }
        effect.map(toSpiStepTransitionalEffect)
      }
      // fallback to step call
      .getOrElse(tryCallStep(stepName))

  }
  @nowarn("msg=deprecated")
  final def getNextStep(
      stepName: String,
      result: BytesPayload,
      userState: Option[BytesPayload],
      workflowContext: WorkflowContext): SpiWorkflow.TransitionalOnlyEffect = {

    val workflow = instanceFactory(workflowContext)

    // if runtime doesn't have a state to provide, we fall back to user's own defined empty state
    val decodedState = decodeUserState(userState).getOrElse(workflow.emptyState())
    workflow._internalSetup(decodedState)

    def applyTransitionFunc(transitionFunc: JFunc[_, _], transitionInputClass: Class[_]) = {
      if (transitionInputClass == null) {
        transitionFunc
          .asInstanceOf[JFunc[Any, TransitionalEffect[Any]]]
          // This is a special case. If transitionInputClass is null,
          // the user provided a supplier in the andThen, therefore we need to call it with 'null' payload.
          // Note, we are calling here a Function[I,O] that is wrapping a Supplier[O].
          // The `I` is ignored and is never used.
          .apply(null)
      } else {
        transitionFunc
          .asInstanceOf[JFunc[Any, TransitionalEffect[Any]]]
          .apply(decodeInput(result, transitionInputClass))
      }
    }

    val descriptor = new WorkflowDescriptor(workflow)

    descriptor.findStepByName(stepName) match {
      case Some(runnableStep: RunnableStep) =>
        val effect = runnableStep.transitionFunc.get()
        toSpiTransitionalEffect(effect)

      case Some(call: CallStep[_, _, _]) =>
        val effect = applyTransitionFunc(call.transitionFunc, call.transitionInputClass)
        toSpiTransitionalEffect(effect)

      case Some(call: AsyncCallStep[_, _, _]) =>
        val effect = applyTransitionFunc(call.transitionFunc, call.transitionInputClass)
        toSpiTransitionalEffect(effect)

      case Some(any) => throw WorkflowStepNotSupported(any.getClass.getSimpleName)
      case None      => throw WorkflowStepNotFound(stepName)
    }

  }

  @nowarn("msg=deprecated") // DeleteState deprecated but must be in here
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
            // deprecated
            throw new IllegalArgumentException("State deletion deprecated")

        }

      case TransitionalEffectImpl(_, _) =>
        // Adding for matching completeness can't happen. Typed API blocks this case.
        throw new IllegalArgumentException("Received transitional effect while processing a command")
    }
  }
  private def handleState(persistence: Persistence[Any]): SpiWorkflow.Persistence =
    persistence match {
      case UpdateState(newState) => new SpiWorkflow.UpdateState(serializer.toBytes(newState))
      case NoPersistence         => SpiWorkflow.NoPersistence
    }

  private def toSpiTransition(transition: Transition): SpiWorkflow.Transition =
    transition match {
      case StepTransition(stepName, input) =>
        new SpiWorkflow.StepTransition(stepName, input.map(serializer.toBytes))
      case Pause        => SpiWorkflow.Pause
      case NoTransition => SpiWorkflow.NoTransition
      case End          => SpiWorkflow.End
      case Delete       => SpiWorkflow.Delete
    }

  private def toSpiTransitionalEffect(effect: Workflow.Effect.TransitionalEffect[_]) =
    effect match {
      case trEff: TransitionalEffectImpl[_] =>
        new SpiWorkflow.TransitionalOnlyEffect(handleState(trEff.persistence), toSpiTransition(trEff.transition))
    }

  private def toSpiStepTransitionalEffect(effect: Workflow.StepEffect): SpiWorkflow.StepTransitionalEffect =
    effect match {
      case stepEff: WorkflowStepEffectImpl[_] =>
        new SpiWorkflow.StepTransitionalEffect(handleState(stepEff.persistence), toSpiTransition(stepEff.transition))
    }

}
