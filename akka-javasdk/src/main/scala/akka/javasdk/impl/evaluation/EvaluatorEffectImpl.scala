/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.evaluation

import akka.annotation.InternalApi
import akka.javasdk.evaluation.Evaluation
import akka.javasdk.evaluation.Evaluator

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] object EvaluatorEffectImpl {
  sealed abstract class PrimaryEffect extends Evaluator.Effect {}

  final case class CompleteEffect(evaluation: Evaluation) extends PrimaryEffect {}

  final case class InconclusiveEffect(reason: String) extends PrimaryEffect {}

  final class Builder extends Evaluator.Effect.Builder {
    override def complete(evaluation: Evaluation): Evaluator.Effect = {
      require(evaluation != null, "Evaluation must not be null")
      CompleteEffect(evaluation)
    }

    override def inconclusive(reason: String): Evaluator.Effect = InconclusiveEffect(reason)
  }

  def builder(): Evaluator.Effect.Builder = new Builder()
}
