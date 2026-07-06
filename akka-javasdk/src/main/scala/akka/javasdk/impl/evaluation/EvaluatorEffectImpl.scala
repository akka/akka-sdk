/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.evaluation

import java.util.concurrent.CompletionStage

import scala.concurrent.Future
import scala.jdk.CollectionConverters._
import scala.jdk.FutureConverters.CompletionStageOps

import akka.annotation.InternalApi
import akka.javasdk.evaluation.Evaluation
import akka.javasdk.evaluation.Evaluator

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] object EvaluatorEffectImpl {
  sealed abstract class PrimaryEffect extends Evaluator.Effect {}

  final case class CompleteEffect(evaluations: Seq[Evaluation]) extends PrimaryEffect {}

  final case class InconclusiveEffect(reason: String) extends PrimaryEffect {}

  final case class AsyncEffect(effect: Future[Evaluator.Effect]) extends PrimaryEffect {}

  class Builder extends Evaluator.Effect.Builder {
    override def complete(evaluation: Evaluation, more: Evaluation*): Evaluator.Effect =
      CompleteEffect(evaluation +: more.toSeq)

    override def complete(evaluations: java.util.List[Evaluation]): Evaluator.Effect = {
      if (evaluations.isEmpty)
        throw new IllegalArgumentException("complete requires at least one evaluation")
      CompleteEffect(evaluations.asScala.toSeq)
    }

    override def inconclusive(reason: String): Evaluator.Effect = InconclusiveEffect(reason)

    override def asyncEffect(futureEffect: CompletionStage[Evaluator.Effect]): Evaluator.Effect =
      AsyncEffect(futureEffect.asScala)
  }

  def builder(): Evaluator.Effect.Builder = new Builder()
}
