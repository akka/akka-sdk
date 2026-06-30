/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testmodels.evaluation;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.EvaluatesAgent;
import akka.javasdk.evaluation.EvaluationContext;
import akka.javasdk.evaluation.Evaluator;

public class EvaluatorTestModels {

  @Component(id = "single-binding-evaluator")
  @EvaluatesAgent(componentId = "support-agent")
  public static class SingleBindingEvaluator extends Evaluator {
    @Override
    public Effect evaluate(EvaluationContext context) {
      return effects().error("not implemented");
    }
  }

  @Component(id = "multi-binding-evaluator")
  @EvaluatesAgent(componentId = "support-agent")
  @EvaluatesAgent(componentId = "billing-agent")
  public static class MultiBindingEvaluator extends Evaluator {
    @Override
    public Effect evaluate(EvaluationContext context) {
      return effects().error("not implemented");
    }
  }

  @Component(id = "no-binding-evaluator")
  public static class NoBindingEvaluator extends Evaluator {
    @Override
    public Effect evaluate(EvaluationContext context) {
      return effects().error("not implemented");
    }
  }
}
