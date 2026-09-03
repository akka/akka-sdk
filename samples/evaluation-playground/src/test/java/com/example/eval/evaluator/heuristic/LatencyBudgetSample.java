package com.example.eval.evaluator.heuristic;

// tag::imports[]
import akka.eval.contract.evaluation.EvaluationResult;
import akka.evalkit.core.domain.EvalContext;
import akka.evalkit.core.metric.LatencyBudget;

import java.time.Duration;
// end::imports[]

/**
 * Reads Interaction.latency() against a stated duration budget. The scoring is
 * proportional past the pass line, so a report can distinguish a run at the edge of the
 * budget from one well under it.
 */
public class LatencyBudgetSample {

    // tag::evaluator[]
    public EvaluationResult score(EvalContext evalContext) {
        var evaluator = LatencyBudget.within(Duration.ofSeconds(2)); // <1>

        return evaluator.evaluate(evalContext.asContext()); // <2>
    }
    // end::evaluator[]

    // tag::interpret[]
    public String describe(EvaluationResult result) {
        return result.score()
            .map(value -> "score " + value // <1>
                + (result.didPass() ? " (within budget)" : " (over budget)")) // <2>
            .orElse("no latency to measure"); // <3>
    }
    // end::interpret[]
}
