/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.impl.workflow

import io.grpc.Status
import akka.platform.javasdk.Metadata
import akka.platform.javasdk.impl.workflow.WorkflowEffectImpl.End
import akka.platform.javasdk.impl.workflow.WorkflowEffectImpl.ErrorEffectImpl
import akka.platform.javasdk.impl.workflow.WorkflowEffectImpl.NoPersistence
import akka.platform.javasdk.impl.workflow.WorkflowEffectImpl.NoTransition
import akka.platform.javasdk.impl.workflow.WorkflowEffectImpl.Pause
import akka.platform.javasdk.impl.workflow.WorkflowEffectImpl.Persistence
import akka.platform.javasdk.impl.workflow.WorkflowEffectImpl.PersistenceEffectBuilderImpl
import akka.platform.javasdk.impl.workflow.WorkflowEffectImpl.Reply
import akka.platform.javasdk.impl.workflow.WorkflowEffectImpl.StepTransition
import akka.platform.javasdk.impl.workflow.WorkflowEffectImpl.Transition
import akka.platform.javasdk.impl.workflow.WorkflowEffectImpl.TransitionalEffectImpl
import akka.platform.javasdk.impl.workflow.WorkflowEffectImpl.UpdateState
import akka.platform.javasdk.workflow.AbstractWorkflow.Effect
import akka.platform.javasdk.workflow.AbstractWorkflow.Effect.Builder
import akka.platform.javasdk.workflow.AbstractWorkflow.Effect.PersistenceEffectBuilder
import akka.platform.javasdk.workflow.AbstractWorkflow.Effect.TransitionalEffect

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
