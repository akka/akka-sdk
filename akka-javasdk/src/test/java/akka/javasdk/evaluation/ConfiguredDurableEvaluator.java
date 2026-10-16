/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.evaluation;

import akka.javasdk.annotations.Component;

/**
 * Example durable evaluator that reads the key {@code key} of its configuration at the start and in
 * its step, and reports both values in its verdict.
 */
@Component(id = "configured-durable-evaluator")
public class ConfiguredDurableEvaluator extends DurableEvaluator<ConfiguredDurableEvaluator.State> {

  public record State(String keyAtStart) {}

  @Override
  public Effect onEvaluation(EvaluationContext context) {
    return effects()
        .updateState(new State(context.config().getString("key")))
        .transitionTo(ConfiguredDurableEvaluator::finish);
  }

  private Effect finish() {
    var keyInStep = evaluationContext().config().getString("key");
    return effects()
        .complete(
            Evaluation.passed(
                currentState().keyAtStart() + " at the start, " + keyInStep + " in the step"));
  }
}
