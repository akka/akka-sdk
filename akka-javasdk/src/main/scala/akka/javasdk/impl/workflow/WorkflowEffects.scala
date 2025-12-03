/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.workflow

import scala.concurrent.duration.FiniteDuration
import scala.jdk.DurationConverters.JavaDurationOps

import akka.annotation.InternalApi
import akka.japi.function
import akka.japi.function.Function
import akka.javasdk.CommandException
import akka.javasdk.Metadata
import akka.javasdk.impl.client.MethodRefResolver
import akka.javasdk.impl.workflow.WorkflowEffects.WorkflowEffectImpl.ErrorEffectImpl
import akka.javasdk.impl.workflow.WorkflowEffects.WorkflowEffectImpl.NoReply
import akka.javasdk.impl.workflow.WorkflowEffects.WorkflowEffectImpl.PersistenceEffectBuilderImpl
import akka.javasdk.impl.workflow.WorkflowEffects.WorkflowEffectImpl.ReadOnlyEffectImpl
import akka.javasdk.impl.workflow.WorkflowEffects.WorkflowEffectImpl.Reply
import akka.javasdk.impl.workflow.WorkflowEffects.WorkflowEffectImpl.TransitionalEffectImpl
import akka.javasdk.impl.workflow.WorkflowEffects.WorkflowStepEffectImpl.toPauseStepEffect
import akka.javasdk.workflow.Workflow
import akka.javasdk.workflow.Workflow.Effect
import akka.javasdk.workflow.Workflow.Effect.PersistenceEffectBuilder
import akka.javasdk.workflow.Workflow.Effect.Transitional
import akka.javasdk.workflow.Workflow.ReadOnlyEffect
import akka.javasdk.workflow.Workflow.StepEffect
import akka.javasdk.workflow.Workflow.TimeoutHandler
import akka.javasdk.workflow.Workflow.WithInput

/**
 * INTERNAL API
 */
@InternalApi
object WorkflowEffects {
  sealed trait Transition

  case class StepTransition[I](stepName: String, input: Option[I]) extends Transition

  sealed trait TimeoutHandler
  case class UnaryTimeoutHandler(handler: akka.japi.function.Function[_, Effect[_]]) extends TimeoutHandler
  case class BinaryTimeoutHandler(handler: akka.japi.function.Function2[_, _, Effect[_]], input: Any)
      extends TimeoutHandler

  case class PauseSettings(duration: FiniteDuration, timeoutHandler: TimeoutHandler)

  case class Pause(reason: Option[String] = None, pauseSettings: Option[PauseSettings] = None) extends Transition

  object NoTransition extends Transition

  case class End(reason: Option[String] = None) extends Transition

  case class Delete(reason: Option[String] = None) extends Transition

  sealed trait Persistence[+S]

  final case class UpdateState[S](newState: S) extends Persistence[S]

  case object NoPersistence extends Persistence[Nothing]

  def createEffectBuilder[S](): WorkflowEffectImpl[S, S] = WorkflowEffectImpl(NoPersistence, Pause(), NoReply)

  def createStepEffectBuilder[S](): WorkflowStepEffectImpl[S] = WorkflowStepEffectImpl(NoPersistence, Pause())

  private def validateReason(reason: String): Unit =
    require(reason != null && reason.nonEmpty, "Given reason must not be null or empty")

  private def toTimeoutHandler(handler: Workflow.TimeoutHandler) = {
    handler match {
      case handler: TimeoutHandler.UnaryTimeoutHandler =>
        UnaryTimeoutHandler(handler.pauseTimeoutHandler())
      case handler: TimeoutHandler.BinaryTimeoutHandler =>
        BinaryTimeoutHandler(handler.pauseTimeoutHandler(), handler.input())
    }
  }

  /**
   * INTERNAL API
   */
  @InternalApi
  object WorkflowEffectImpl {

    sealed trait Reply[+R]
    case class ReplyValue[R](value: R, metadata: Metadata) extends Reply[R]
    case object NoReply extends Reply[Nothing]

    final case class PersistenceEffectBuilderImpl[S](persistence: Persistence[S]) extends PersistenceEffectBuilder[S] {

      override def transitionTo[W](lambda: Function[W, Workflow.StepEffect]): Transitional = {
        val method = MethodRefResolver.resolveMethodRef(lambda)
        val stepName = WorkflowDescriptor.stepMethodName(method)
        TransitionalEffectImpl(persistence, StepTransition(stepName, None))
      }

      override def transitionTo[W, I](
          lambda: function.Function2[W, I, Workflow.StepEffect]): WithInput[I, Transitional] = {
        val method = MethodRefResolver.resolveMethodRef(lambda)
        val stepName = WorkflowDescriptor.stepMethodName(method)
        EffectCallWithInputImpl(persistence, stepName)
      }

      override def pause(): Transitional =
        TransitionalEffectImpl(persistence, Pause())

      override def pause(reason: String): Transitional = {
        validateReason(reason)
        TransitionalEffectImpl(persistence, Pause(Some(reason)))
      }

      override def pause(pauseSettings: Workflow.PauseSettings): Transitional = {
        TransitionalEffectImpl(
          persistence,
          Pause(
            None,
            Some(
              WorkflowEffects
                .PauseSettings(pauseSettings.duration().toScala, toTimeoutHandler(pauseSettings.timeoutHandler())))))
      }

      override def end(): Transitional =
        TransitionalEffectImpl(persistence, End())

      override def end(reason: String): Transitional = {
        validateReason(reason)
        TransitionalEffectImpl(persistence, End(Some(reason)))
      }

      override def delete(): Transitional =
        TransitionalEffectImpl(persistence, Delete())

      override def delete(reason: String): Transitional = {
        validateReason(reason)
        TransitionalEffectImpl(persistence, Delete(Some(reason)))
      }

      override def transitionTo[I](stepName: String, input: I): Transitional =
        TransitionalEffectImpl(persistence, StepTransition(stepName, Some(input)))

      override def transitionTo(stepName: String): Transitional =
        TransitionalEffectImpl(persistence, StepTransition(stepName, None))

    }

    final case class TransitionalEffectImpl[S](persistence: Persistence[S], transition: Transition)
        extends Transitional {

      override def thenReply[R](message: R): Effect[R] =
        WorkflowEffectImpl(persistence, transition, ReplyValue(message, Metadata.EMPTY))

      override def thenReply[R](message: R, metadata: Metadata): Effect[R] =
        WorkflowEffectImpl(persistence, transition, ReplyValue(message, metadata))
    }

    final case class ReadOnlyEffectImpl[T]() extends ReadOnlyEffect[T] {

      def reply[R](message: R): ReadOnlyEffect[R] =
        WorkflowEffectImpl(NoPersistence, NoTransition, ReplyValue(message, Metadata.EMPTY))

      def reply[R](message: R, metadata: Metadata): ReadOnlyEffect[R] =
        WorkflowEffectImpl(NoPersistence, NoTransition, ReplyValue(message, metadata))
    }

    final case class ErrorEffectImpl[R](description: String, exception: Option[CommandException])
        extends ReadOnlyEffect[R]

  }

  /**
   * INTERNAL API
   */
  @InternalApi
  case class WorkflowEffectImpl[S, T](persistence: Persistence[S], transition: Transition, reply: Reply[T])
      extends Effect.Builder[S]
      with ReadOnlyEffect[T]
      with Effect[T] {

    override def updateState(newState: S): PersistenceEffectBuilder[S] =
      PersistenceEffectBuilderImpl(UpdateState(newState))

    override def pause(): Transitional =
      TransitionalEffectImpl(NoPersistence, Pause())

    override def pause(reason: String): Transitional = {
      validateReason(reason)
      TransitionalEffectImpl(NoPersistence, Pause(Some(reason)))
    }

    override def pause(pauseSettings: Workflow.PauseSettings): Transitional = {
      TransitionalEffectImpl(
        persistence,
        Pause(
          None,
          Some(
            WorkflowEffects
              .PauseSettings(pauseSettings.duration().toScala, toTimeoutHandler(pauseSettings.timeoutHandler())))))
    }

    override def transitionTo[I](stepName: String, input: I): Transitional =
      TransitionalEffectImpl(NoPersistence, StepTransition(stepName, Some(input)))

    override def transitionTo(stepName: String): Transitional =
      TransitionalEffectImpl(NoPersistence, StepTransition(stepName, None))

    override def end(): Transitional =
      TransitionalEffectImpl(NoPersistence, End())

    override def end(reason: String): Transitional = {
      validateReason(reason)
      TransitionalEffectImpl(NoPersistence, End(Some(reason)))
    }

    override def delete(): Transitional =
      TransitionalEffectImpl(NoPersistence, Delete())

    override def delete(reason: String): Transitional = {
      validateReason(reason)
      TransitionalEffectImpl(NoPersistence, Delete(Some(reason)))
    }

    override def reply[R](reply: R): ReadOnlyEffect[R] =
      ReadOnlyEffectImpl().reply(reply)

    override def reply[R](reply: R, metadata: Metadata): ReadOnlyEffect[R] =
      ReadOnlyEffectImpl().reply(reply, metadata)

    override def transitionTo[W](lambda: Function[W, Workflow.StepEffect]): Transitional = {
      val method = MethodRefResolver.resolveMethodRef(lambda)
      val stepName = WorkflowDescriptor.stepMethodName(method)
      TransitionalEffectImpl(persistence, StepTransition(stepName, None))
    }

    override def transitionTo[W, I](
        lambda: function.Function2[W, I, Workflow.StepEffect]): WithInput[I, Transitional] = {
      val method = MethodRefResolver.resolveMethodRef(lambda)
      val stepName = WorkflowDescriptor.stepMethodName(method)
      EffectCallWithInputImpl(NoPersistence, stepName)
    }

    override def error[R](description: String): ReadOnlyEffect[R] =
      error(new CommandException(description))

    override def error[R](commandException: CommandException): ReadOnlyEffect[R] =
      ErrorEffectImpl(commandException.getMessage, Some(commandException))

  }

  private final case class EffectCallWithInputImpl[I, S](persistence: Persistence[S], stepName: String)
      extends WithInput[I, Transitional] {
    override def withInput(input: I): Transitional =
      TransitionalEffectImpl(persistence, StepTransition(stepName, Some(input)))
  }

  /**
   * INTERNAL API
   */
  @InternalApi
  object WorkflowStepEffectImpl {

    private final case class PersistenceEffectBuilderImpl[S](persistence: Persistence[S])
        extends StepEffect.PersistenceEffectBuilder {

      def thenTransitionTo[W](lambda: Function[W, Workflow.StepEffect]): Workflow.StepEffect = {
        val method = MethodRefResolver.resolveMethodRef(lambda)
        val stepName = WorkflowDescriptor.stepMethodName(method)
        WorkflowStepEffectImpl(persistence, StepTransition(stepName, None))
      }
      override def thenTransitionTo[W, I](lambda: function.Function2[W, I, StepEffect]): WithInput[I, StepEffect] = {
        val method = MethodRefResolver.resolveMethodRef(lambda)
        val stepName = WorkflowDescriptor.stepMethodName(method)
        StepEffectCallWithInputImpl(persistence, stepName)
      }

      override def thenPause(): StepEffect =
        WorkflowStepEffectImpl(persistence, Pause())

      override def thenPause(reason: String): StepEffect = {
        validateReason(reason)
        WorkflowStepEffectImpl(persistence, Pause(Some(reason)))
      }

      override def thenPause(pauseSettings: Workflow.PauseSettings): StepEffect = {
        toPauseStepEffect(persistence, pauseSettings)
      }

      override def thenDelete(): StepEffect =
        WorkflowStepEffectImpl(persistence, Delete())

      override def thenDelete(reason: String): StepEffect = {
        validateReason(reason)
        WorkflowStepEffectImpl(persistence, Delete(Some(reason)))
      }

      override def thenEnd(): StepEffect =
        WorkflowStepEffectImpl(persistence, End())

      override def thenEnd(reason: String): StepEffect = {
        validateReason(reason)
        WorkflowStepEffectImpl(persistence, End(Some(reason)))
      }
    }

    private def toPauseStepEffect[S](persistence: Persistence[S], pauseSettings: Workflow.PauseSettings) = {
      WorkflowStepEffectImpl(
        persistence,
        Pause(
          None,
          Some(
            WorkflowEffects
              .PauseSettings(pauseSettings.duration().toScala, toTimeoutHandler(pauseSettings.timeoutHandler())))))
    }
  }

  /**
   * INTERNAL API
   */
  @InternalApi
  case class WorkflowStepEffectImpl[S](persistence: Persistence[S], transition: Transition)
      extends StepEffect.Builder[S]
      with StepEffect {

    override def updateState(newState: S): StepEffect.PersistenceEffectBuilder =
      WorkflowStepEffectImpl.PersistenceEffectBuilderImpl(UpdateState(newState))

    override def thenPause(): StepEffect =
      WorkflowStepEffectImpl(NoPersistence, Pause())

    override def thenPause(reason: String): StepEffect = {
      validateReason(reason)
      WorkflowStepEffectImpl(NoPersistence, Pause(Some(reason)))
    }

    override def thenPause(pauseSettings: Workflow.PauseSettings): StepEffect = {
      toPauseStepEffect(persistence, pauseSettings)
    }

    def thenTransitionTo[W](lambda: Function[W, Workflow.StepEffect]): Workflow.StepEffect = {
      val method = MethodRefResolver.resolveMethodRef(lambda)
      val stepName = WorkflowDescriptor.stepMethodName(method)
      WorkflowStepEffectImpl(persistence, StepTransition(stepName, None))
    }

    override def thenTransitionTo[W, I](lambda: function.Function2[W, I, StepEffect]): WithInput[I, StepEffect] = {
      val method = MethodRefResolver.resolveMethodRef(lambda)
      val stepName = WorkflowDescriptor.stepMethodName(method)
      StepEffectCallWithInputImpl(persistence, stepName)
    }

    override def thenEnd(): StepEffect =
      WorkflowStepEffectImpl(NoPersistence, End())

    override def thenEnd(reason: String): StepEffect = {
      validateReason(reason)
      WorkflowStepEffectImpl(NoPersistence, End(Some(reason)))
    }

    override def thenDelete(): StepEffect =
      WorkflowStepEffectImpl(NoPersistence, Delete())

    override def thenDelete(reason: String): StepEffect = {
      validateReason(reason)
      WorkflowStepEffectImpl(NoPersistence, Delete(Some(reason)))
    }

  }

  private final case class StepEffectCallWithInputImpl[I, S](persistence: Persistence[S], stepName: String)
      extends WithInput[I, StepEffect] {
    def withInput(input: I): StepEffect =
      WorkflowStepEffectImpl(persistence, StepTransition(stepName, Some(input)))
  }
}
