package com.example.eval.scorer.deterministic;

// tag::imports[]
import akka.evalkit.core.domain.Observation;
import akka.evalkit.core.domain.RunOutcome;
import akka.evalkit.core.domain.Scorer;
import akka.evalkit.core.metric.MetricRef;
// end::imports[]

/**
 * A customer-authored deterministic scorer for a scenario that requires the reply to
 * name the refund window.
 */
// tag::class[]
public final class RefundWindowScorer implements Scorer {

    private static final MetricRef REF = new MetricRef("refund_window_named", 1); // <1>

    @Override
    public String id() {
        return REF.label(); // <2>
    }

    @Override
    public RunOutcome score(Observation observation) {
        String reply = observation.interaction().responseText();

        if (reply == null || reply.isBlank()) { // <3>
            return new RunOutcome.Inconclusive("no reply to read");
        }

        boolean stated = reply.matches("(?i).*\\b30[- ]day\\b.*"); // <4>

        return new RunOutcome.Asserted(
            stated,
            "30-day refund window",
            stated ? "found in the reply" : "not stated in the reply"); // <5>
    }
}
// end::class[]
