/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testmodels.evaluation;

import akka.javasdk.annotations.Component;
import akka.javasdk.evaluation.EvaluationContext;
import akka.javasdk.evaluation.Evaluator;

public class EvaluatorTestModels {

  @Component(id = "some-evaluator")
  public static class SomeEvaluator extends Evaluator {
    @Override
    public Effect evaluate(EvaluationContext context) {
      return effects().inconclusive("not implemented");
    }
  }
}
