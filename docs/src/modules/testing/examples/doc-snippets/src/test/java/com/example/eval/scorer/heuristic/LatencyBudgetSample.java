package com.example.eval.scorer.heuristic;

// tag::imports[]
import akka.evalkit.core.domain.Observation;
import akka.evalkit.core.domain.RunOutcome;
import akka.evalkit.core.metric.LatencyBudget;

import java.time.Duration;
// end::imports[]

/**
 * Reads Interaction.latency() against a stated duration budget. The scoring is
 * proportional past the pass line, so a report can tell a run that squeaked in from one
 * that walked in comfortably.
 */
public class LatencyBudgetSample {

    // tag::scorer[]
    public RunOutcome score(Observation observation) {
        var scorer = LatencyBudget.within(Duration.ofSeconds(2)); // <1>

        return scorer.score(observation); // <2>
    }
    // end::scorer[]

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
