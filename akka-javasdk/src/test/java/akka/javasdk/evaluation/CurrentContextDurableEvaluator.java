/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.evaluation;

import akka.javasdk.annotations.Component;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;

/**
 * Reports the value of {@link #KEY} in the current OpenTelemetry context, at the start and in a
 * step.
 */
@Component(id = "current-context-durable-evaluator")
public class CurrentContextDurableEvaluator
    extends DurableEvaluator<CurrentContextDurableEvaluator.State> {

  public static final ContextKey<String> KEY = ContextKey.named("test");

  public record State(String valueAtStart) {}

  @Override
  public Effect onEvaluation(EvaluationContext context) {
    return effects()
        .updateState(new State(Context.current().get(KEY)))
        .transitionTo(CurrentContextDurableEvaluator::finish);
  }

  private Effect finish() {
    var valueInStep = Context.current().get(KEY);
    return effects()
        .complete(
            Evaluation.passed(
                currentState().valueAtStart() + " at the start, " + valueInStep + " in the step"));
  }
}
