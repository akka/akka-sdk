package com.example.eval.evaluator.heuristic;

// tag::imports[]
import akka.evalkit.core.domain.EvalContext;
import akka.evalkit.core.domain.RunOutcome;
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
    public RunOutcome score(EvalContext evalContext) {
        var evaluator = LatencyBudget.within(Duration.ofSeconds(2)); // <1>

        return evaluator.score(evalContext); // <2>
    }
    // end::evaluator[]

    // tag::interpret[]
    public String describe(RunOutcome outcome) {
        if (outcome instanceof RunOutcome.Measured measured) { // <1>
            return "score " + measured.value() // <2>
                + (measured.passed() ? " (within budget)" : " (over budget)");
        }
        return "no latency to measure"; // <3>
    }
    // end::interpret[]
}
