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
import akka.javasdk.workflow.Workflow.CommandHandler
import akka.javasdk.workflow.Workflow.CommandHandler.BinaryCommandHandler
import akka.javasdk.workflow.Workflow.CommandHandler.UnaryCommandHandler
import akka.javasdk.workflow.Workflow.Effect
import akka.javasdk.workflow.Workflow.Effect.PersistenceEffectBuilder
import akka.javasdk.workflow.Workflow.Effect.Transitional
import akka.javasdk.workflow.Workflow.ReadOnlyEffect
import akka.javasdk.workflow.Workflow.StepEffect
import akka.javasdk.workflow.Workflow.WithInput

/**
 * INTERNAL API
 */
@InternalApi
object WorkflowEffects {
  sealed trait Transition

  case class StepTransition[I](stepName: String, input: Option[I]) extends Transition

  sealed trait CommandHandler
  case class UnaryCommandHandler(handler: akka.japi.function.Function[_, Effect[_]]) extends CommandHandler
  case class BinaryCommandHandler(handler: akka.japi.function.Function2[_, _, Effect[_]], input: Any)
      extends CommandHandler

  case class PauseSettings(duration: FiniteDuration, timeoutHandler: CommandHandler)

  case class PauseTransition(reason: Option[String], pauseSettings: Option[PauseSettings] = None) extends Transition
  object PauseTransition {
    val noReason: PauseTransition = PauseTransition(None)
    def withReason(reason: String): PauseTransition = PauseTransition(Some(reason))
  }

  object NoTransition extends Transition

  case class EndTransition(reason: Option[String]) extends Transition
  object EndTransition {
    def noReason: EndTransition = EndTransition(None)
    def withReason(reason: String): EndTransition = EndTransition(Some(reason))
  }

  case class DeleteTransition(reason: Option[String]) extends Transition
  object DeleteTransition {
    def noReason: DeleteTransition = DeleteTransition(None)
    def withReason(reason: String): DeleteTransition = DeleteTransition(Some(reason))
  }
  sealed trait Persistence[+S]

  final case class UpdateState[S](newState: S) extends Persistence[S]

  case object NoPersistence extends Persistence[Nothing]

  def createEffectBuilder[S](): WorkflowEffectImpl[S, S] =
    WorkflowEffectImpl(NoPersistence, PauseTransition(None), NoReply)

  def createStepEffectBuilder[S](): WorkflowStepEffectImpl[S] =
    WorkflowStepEffectImpl(NoPersistence, PauseTransition(None))

  private def validateReason(reason: String): Unit =
    require(reason != null && reason.nonEmpty, "Given reason must not be null or empty")

  private def toTimeoutHandler(handler: Workflow.CommandHandler) = {
    handler match {
      case handler: CommandHandler.UnaryCommandHandler =>
        UnaryCommandHandler(handler.handler())
      case handler: CommandHandler.BinaryCommandHandler =>
        BinaryCommandHandler(handler.handler(), handler.input())
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
        TransitionalEffectImpl(persistence, PauseTransition.noReason)

      override def pause(reason: String): Transitional = {
        validateReason(reason)
        TransitionalEffectImpl(persistence, PauseTransition.withReason(reason))
      }

      override def pause(pauseSettings: Workflow.PauseSettings): Transitional = {
        TransitionalEffectImpl(
          persistence,
          PauseTransition(
            None,
            Some(
              WorkflowEffects
                .PauseSettings(pauseSettings.duration().toScala, toTimeoutHandler(pauseSettings.timeoutHandler())))))
      }

      override def end(): Transitional =
        TransitionalEffectImpl(persistence, EndTransition.noReason)

      override def end(reason: String): Transitional = {
        validateReason(reason)
        TransitionalEffectImpl(persistence, EndTransition.withReason(reason))
      }

      override def delete(): Transitional =
        TransitionalEffectImpl(persistence, DeleteTransition.noReason)

      override def delete(reason: String): Transitional = {
        validateReason(reason)
        TransitionalEffectImpl(persistence, DeleteTransition.withReason(reason))
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
      TransitionalEffectImpl(NoPersistence, PauseTransition.noReason)

    override def pause(reason: String): Transitional = {
      validateReason(reason)
      TransitionalEffectImpl(NoPersistence, PauseTransition.withReason(reason))
    }

    override def pause(pauseSettings: Workflow.PauseSettings): Transitional = {
      TransitionalEffectImpl(
        persistence,
        PauseTransition(
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
      TransitionalEffectImpl(NoPersistence, EndTransition.noReason)

    override def end(reason: String): Transitional = {
      validateReason(reason)
      TransitionalEffectImpl(NoPersistence, EndTransition.withReason(reason))
    }

    override def delete(): Transitional =
      TransitionalEffectImpl(NoPersistence, DeleteTransition.noReason)

    override def delete(reason: String): Transitional = {
      validateReason(reason)
      TransitionalEffectImpl(NoPersistence, DeleteTransition.withReason(reason))
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
        WorkflowStepEffectImpl(persistence, PauseTransition.noReason)

      override def thenPause(reason: String): StepEffect = {
        validateReason(reason)
        WorkflowStepEffectImpl(persistence, PauseTransition.withReason(reason))
      }

      override def thenPause(pauseSettings: Workflow.PauseSettings): StepEffect = {
        toPauseStepEffect(persistence, pauseSettings)
      }

      override def thenDelete(): StepEffect =
        WorkflowStepEffectImpl(persistence, DeleteTransition.noReason)

      override def thenDelete(reason: String): StepEffect = {
        validateReason(reason)
        WorkflowStepEffectImpl(persistence, DeleteTransition.withReason(reason))
      }

      override def thenEnd(): StepEffect =
        WorkflowStepEffectImpl(persistence, EndTransition.noReason)

      override def thenEnd(reason: String): StepEffect = {
        validateReason(reason)
        WorkflowStepEffectImpl(persistence, EndTransition.withReason(reason))
      }
    }

    private def toPauseStepEffect[S](persistence: Persistence[S], pauseSettings: Workflow.PauseSettings) = {
      WorkflowStepEffectImpl(
        persistence,
        PauseTransition(
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
      WorkflowStepEffectImpl(NoPersistence, PauseTransition.noReason)

    override def thenPause(reason: String): StepEffect = {
      validateReason(reason)
      WorkflowStepEffectImpl(NoPersistence, PauseTransition.withReason(reason))
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
      WorkflowStepEffectImpl(NoPersistence, EndTransition.noReason)

    override def thenEnd(reason: String): StepEffect = {
      validateReason(reason)
      WorkflowStepEffectImpl(NoPersistence, EndTransition.withReason(reason))
    }

    override def thenDelete(): StepEffect =
      WorkflowStepEffectImpl(NoPersistence, DeleteTransition.noReason)

    override def thenDelete(reason: String): StepEffect = {
      validateReason(reason)
      WorkflowStepEffectImpl(NoPersistence, DeleteTransition.withReason(reason))
    }

  }

  private final case class StepEffectCallWithInputImpl[I, S](persistence: Persistence[S], stepName: String)
      extends WithInput[I, StepEffect] {
    def withInput(input: I): StepEffect =
      WorkflowStepEffectImpl(persistence, StepTransition(stepName, Some(input)))
  }
}
