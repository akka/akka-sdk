/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.workflow

import akka.annotation.InternalApi
import akka.javasdk.Metadata
import akka.javasdk.impl.workflow.WorkflowEffects.WorkflowEffectImpl.ErrorEffectImpl
import akka.javasdk.impl.workflow.WorkflowEffects.WorkflowEffectImpl.NoReply
import akka.javasdk.impl.workflow.WorkflowEffects.WorkflowEffectImpl.PersistenceEffectBuilderImpl
import akka.javasdk.impl.workflow.WorkflowEffects.WorkflowEffectImpl.ReadOnlyEffectImpl
import akka.javasdk.impl.workflow.WorkflowEffects.WorkflowEffectImpl.Reply
import akka.javasdk.impl.workflow.WorkflowEffects.WorkflowEffectImpl.TransitionalEffectImpl
import akka.javasdk.impl.workflow.WorkflowEffects.WorkflowStepEffectImpl.ErrorStepEffectImpl
import akka.javasdk.workflow.Workflow.Effect
import akka.javasdk.workflow.Workflow.Effect.PersistenceEffectBuilder
import akka.javasdk.workflow.Workflow.Effect.TransitionalEffect
import akka.javasdk.workflow.Workflow.ReadOnlyEffect
import akka.javasdk.workflow.Workflow.StepEffect
import io.grpc.Status

/**
 * INTERNAL API
 */
@InternalApi
object WorkflowEffects {
  sealed trait Transition

  case class StepTransition[I](stepName: String, input: Option[I]) extends Transition

  object Pause extends Transition

  object NoTransition extends Transition

  object End extends Transition

  object Delete extends Transition

  sealed trait Persistence[+S]

  final case class UpdateState[S](newState: S) extends Persistence[S]

  case object NoPersistence extends Persistence[Nothing]

  def createEffectBuilder[S](): WorkflowEffectImpl[S, S] = WorkflowEffectImpl(NoPersistence, Pause, NoReply)

  def createStepEffectBuilder[S](): WorkflowStepEffectImpl[S] = WorkflowStepEffectImpl(NoPersistence, Pause)

  /**
   * INTERNAL API
   */
  @InternalApi
  object WorkflowEffectImpl {

    sealed trait Reply[+R]
    case class ReplyValue[R](value: R, metadata: Metadata) extends Reply[R]
    case object NoReply extends Reply[Nothing]

    final case class PersistenceEffectBuilderImpl[S](persistence: Persistence[S]) extends PersistenceEffectBuilder[S] {

      override def pause(): TransitionalEffect[Void] =
        TransitionalEffectImpl(persistence, Pause)

      override def transitionTo[I](stepName: String, input: I): TransitionalEffect[Void] =
        TransitionalEffectImpl(persistence, StepTransition(stepName, Some(input)))

      override def transitionTo(stepName: String): TransitionalEffect[Void] =
        TransitionalEffectImpl(persistence, StepTransition(stepName, None))

      override def end(): TransitionalEffect[Void] =
        TransitionalEffectImpl(persistence, End)

      override def delete(): TransitionalEffect[Void] =
        TransitionalEffectImpl(persistence, Delete)
    }

    final case class TransitionalEffectImpl[S, T](persistence: Persistence[S], transition: Transition)
        extends TransitionalEffect[T] {

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

    final case class ErrorEffectImpl[R](description: String, status: Option[Status.Code]) extends ReadOnlyEffect[R]

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

    override def pause(): TransitionalEffect[Void] =
      TransitionalEffectImpl(NoPersistence, Pause)

    override def transitionTo[I](stepName: String, input: I): TransitionalEffect[Void] =
      TransitionalEffectImpl(NoPersistence, StepTransition(stepName, Some(input)))

    override def transitionTo(stepName: String): TransitionalEffect[Void] =
      TransitionalEffectImpl(NoPersistence, StepTransition(stepName, None))

    override def end(): TransitionalEffect[Void] =
      TransitionalEffectImpl(NoPersistence, End)

    override def delete(): TransitionalEffect[Void] =
      TransitionalEffectImpl(NoPersistence, Delete)

    override def reply[R](reply: R): ReadOnlyEffect[R] =
      ReadOnlyEffectImpl().reply(reply)

    override def reply[R](reply: R, metadata: Metadata): ReadOnlyEffect[R] =
      ReadOnlyEffectImpl().reply(reply, metadata)

    override def error[R](description: String): ReadOnlyEffect[R] =
      ErrorEffectImpl(description, Some(Status.Code.INVALID_ARGUMENT))

  }

  /**
   * INTERNAL API
   */
  @InternalApi
  object WorkflowStepEffectImpl {

    final private case class PersistenceEffectBuilderImpl[S](persistence: Persistence[S])
        extends StepEffect.PersistenceEffectBuilder {

      override def transitionTo(stepName: String): StepEffect =
        StepEffectImpl(persistence, StepTransition(stepName, None))

      override def pause(): StepEffect =
        StepEffectImpl(persistence, Pause)

      override def delete(): StepEffect =
        StepEffectImpl(persistence, Delete)

      override def end(): StepEffect =
        StepEffectImpl(persistence, End)

      override def error(description: String): StepEffect =
        ErrorStepEffectImpl(description)
    }

    final case class StepEffectImpl[S, T](persistence: Persistence[S], transition: Transition) extends StepEffect {}
    final case class ErrorStepEffectImpl[R](description: String) extends StepEffect
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

    override def pause(): StepEffect =
      WorkflowStepEffectImpl(NoPersistence, Pause)

    override def transitionTo[I](stepName: String, input: I): StepEffect =
      WorkflowStepEffectImpl(NoPersistence, StepTransition(stepName, Some(input)))

    override def transitionTo(stepName: String): StepEffect =
      WorkflowStepEffectImpl(NoPersistence, StepTransition(stepName, None))

    override def end(): StepEffect =
      WorkflowStepEffectImpl(NoPersistence, End)

    override def delete(): StepEffect =
      WorkflowStepEffectImpl(NoPersistence, Delete)

    override def error(description: String): StepEffect =
      ErrorStepEffectImpl(description)
  }

}
