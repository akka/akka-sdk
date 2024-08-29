/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.workflow

import io.grpc.Status
import WorkflowEffectImpl.End
import WorkflowEffectImpl.ErrorEffectImpl
import WorkflowEffectImpl.NoPersistence
import WorkflowEffectImpl.NoTransition
import WorkflowEffectImpl.Pause
import WorkflowEffectImpl.Persistence
import WorkflowEffectImpl.PersistenceEffectBuilderImpl
import WorkflowEffectImpl.Reply
import WorkflowEffectImpl.StepTransition
import WorkflowEffectImpl.Transition
import WorkflowEffectImpl.TransitionalEffectImpl
import WorkflowEffectImpl.UpdateState
import akka.annotation.InternalApi
import akka.javasdk.Metadata
import akka.javasdk.workflow.Workflow.Effect
import akka.javasdk.workflow.Workflow.Effect.Builder
import akka.javasdk.workflow.Workflow.Effect.PersistenceEffectBuilder
import akka.javasdk.workflow.Workflow.Effect.TransitionalEffect

/**
 * INTERNAL API
 */
@InternalApi
object WorkflowEffectImpl {

  sealed trait Transition
  case class StepTransition[I](stepName: String, input: Option[I]) extends Transition
  object Pause extends Transition
  object NoTransition extends Transition
  object End extends Transition

  sealed trait Persistence[+S]
  final case class UpdateState[S](newState: S) extends Persistence[S]
  case object DeleteState extends Persistence[Nothing]
  case object NoPersistence extends Persistence[Nothing]

  sealed trait Reply[+R]
  case class ReplyValue[R](value: R, metadata: Metadata) extends Reply[R]
  case object NoReply extends Reply[Nothing]

  def apply[S](): WorkflowEffectImpl[S, S] = WorkflowEffectImpl(NoPersistence, Pause, NoReply)

  final case class PersistenceEffectBuilderImpl[S](persistence: Persistence[S]) extends PersistenceEffectBuilder[S] {

    override def pause(): TransitionalEffect[Void] =
      TransitionalEffectImpl(persistence, Pause)

    override def transitionTo[I](stepName: String, input: I): TransitionalEffect[Void] =
      TransitionalEffectImpl(persistence, StepTransition(stepName, Some(input)))

    override def transitionTo(stepName: String): TransitionalEffect[Void] =
      TransitionalEffectImpl(persistence, StepTransition(stepName, None))

    override def end(): TransitionalEffect[Void] =
      TransitionalEffectImpl(persistence, End)
  }

  final case class TransitionalEffectImpl[S, T](persistence: Persistence[S], transition: Transition)
      extends TransitionalEffect[T] {

    override def thenReply[R](message: R): Effect[R] =
      WorkflowEffectImpl(persistence, transition, ReplyValue(message, Metadata.EMPTY))

    override def thenReply[R](message: R, metadata: Metadata): Effect[R] =
      WorkflowEffectImpl(persistence, transition, ReplyValue(message, metadata))
  }

  final case class ErrorEffectImpl[R](description: String, status: Option[Status.Code]) extends Effect.ErrorEffect[R]
}

/**
 * INTERNAL API
 */
@InternalApi
case class WorkflowEffectImpl[S, T](persistence: Persistence[S], transition: Transition, reply: Reply[T])
    extends Builder[S]
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

  override def reply[R](reply: R): Effect[R] =
    TransitionalEffectImpl(NoPersistence, NoTransition).thenReply(reply)

  override def reply[R](reply: R, metadata: Metadata): Effect[R] =
    TransitionalEffectImpl(NoPersistence, NoTransition).thenReply(reply, metadata)

  override def error[R](description: String): Effect.ErrorEffect[R] =
    ErrorEffectImpl(description, None)

}
