/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.evaluation;

import akka.javasdk.evaluation.Evaluation;
import akka.javasdk.evaluation.EvaluationContext;
import akka.javasdk.evaluation.Evaluator;

/** An evaluator used in unit tests to exercise {@link EvaluationContext#interaction()}. */
public class InteractionAccessorEvaluator extends Evaluator {

  @Override
  public Effect evaluate(EvaluationContext context) {
    var interaction = context.interaction();
    if (interaction.isEmpty()) {
      return effects().inconclusive("no interaction content for this subject");
    }
    return effects()
        .complete(
            Evaluation.passed("read interaction content")
                .withAttribute("finalText", interaction.get().finalResponseText()));
  }
}
