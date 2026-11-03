/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.evaluation;

import akka.javasdk.evaluation.Evaluation;
import akka.javasdk.evaluation.EvaluationContext;
import akka.javasdk.evaluation.Evaluator;

/** Passes when the score in its configuration reaches the configured threshold. */
public class ThresholdEvaluator extends Evaluator {

  @Override
  public Effect evaluate(EvaluationContext context) {
    double threshold = context.config().getDouble("threshold");
    double score = context.config().getDouble("score");
    if (score >= threshold) {
      return effects().complete(Evaluation.passed("score " + score).withScore(score));
    } else {
      return effects().complete(Evaluation.failed("score " + score).withScore(score));
    }
  }
}
