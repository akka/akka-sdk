/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.evaluation

import akka.annotation.InternalApi
import akka.japi.function
import akka.javasdk.evaluation.Evaluation
import akka.javasdk.evaluation.WorkflowEvaluator.Effect
import akka.javasdk.evaluation.WorkflowEvaluator.WithInput
import akka.javasdk.impl.client.MethodRefResolver
import akka.javasdk.impl.workflow.WorkflowDescriptor

/**
 * INTERNAL API
 *
 * Effect model for [[akka.javasdk.evaluation.WorkflowEvaluator]]. A deliberately small subset of the workflow effects:
 * state update plus either a step transition or a terminal evaluation outcome (complete / inconclusive). There is no
 * pause, end, delete or reply — the component's lifecycle guarantees are enforced by construction.
 */
@InternalApi
private[javasdk] object WorkflowEvaluatorEffects {

  sealed trait Transition
  final case class StepTransition(stepName: String, input: Option[Any], declaringClass: Class[_]) extends Transition
  final case class CompleteTransition(evaluation: Evaluation) extends Transition
  final case class InconclusiveTransition(reason: String) extends Transition

  sealed trait Persistence[+S]
  final case class UpdateState[S](newState: S) extends Persistence[S] {
    require(newState != null, "Updated state must not be null")
  }
  case object NoPersistence extends Persistence[Nothing]

  def createBuilder[S](): Effect.Builder[S] = BuilderImpl(NoPersistence)

  final case class EffectImpl[S](persistence: Persistence[S], transition: Transition) extends Effect

  private final case class StepRef(stepName: String, declaringClass: Class[_])

  private def resolveStep(methodRef: AnyRef): StepRef = {
    val method = MethodRefResolver.resolveMethodRef(methodRef)
    StepRef(WorkflowDescriptor.stepMethodName(method), method.getDeclaringClass)
  }

  private def completeEffect[S](persistence: Persistence[S], evaluation: Evaluation): Effect =
    EffectImpl(persistence, CompleteTransition(evaluation))

  private def inconclusiveEffect[S](persistence: Persistence[S], reason: String): Effect = {
    require(reason != null && reason.nonEmpty, "Given reason must not be null or empty")
    EffectImpl(persistence, InconclusiveTransition(reason))
  }

  private def transitionEffect[S](persistence: Persistence[S], methodRef: AnyRef): Effect = {
    val step = resolveStep(methodRef)
    EffectImpl(persistence, StepTransition(step.stepName, None, step.declaringClass))
  }

  private def transitionWithInput[S, I](persistence: Persistence[S], methodRef: AnyRef): WithInput[I, Effect] = {
    val step = resolveStep(methodRef)
    (input: I) => EffectImpl(persistence, StepTransition(step.stepName, Some(input), step.declaringClass))
  }

  final case class BuilderImpl[S](persistence: Persistence[S]) extends Effect.Builder[S] {

    override def updateState(newState: S): Effect.PersistenceEffectBuilder[S] =
      PersistenceEffectBuilderImpl(UpdateState(newState))

    override def transitionTo[W](methodRef: function.Function[W, Effect]): Effect =
      transitionEffect(persistence, methodRef)

    override def transitionTo[W, I](methodRef: function.Function2[W, I, Effect]): WithInput[I, Effect] =
      transitionWithInput(persistence, methodRef)

    override def complete(evaluation: Evaluation): Effect =
      completeEffect(persistence, evaluation)

    override def inconclusive(reason: String): Effect =
      inconclusiveEffect(persistence, reason)
  }

  final case class PersistenceEffectBuilderImpl[S](persistence: Persistence[S])
      extends Effect.PersistenceEffectBuilder[S] {

    override def transitionTo[W](methodRef: function.Function[W, Effect]): Effect =
      transitionEffect(persistence, methodRef)

    override def transitionTo[W, I](methodRef: function.Function2[W, I, Effect]): WithInput[I, Effect] =
      transitionWithInput(persistence, methodRef)

    override def complete(evaluation: Evaluation): Effect =
      completeEffect(persistence, evaluation)

    override def inconclusive(reason: String): Effect =
      inconclusiveEffect(persistence, reason)
  }
}
