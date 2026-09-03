package com.example.eval.evaluator.deterministic;

// tag::imports[]
import akka.eval.contract.evaluation.EvaluationContext;
import akka.eval.contract.evaluation.EvaluationResult;
import akka.eval.contract.evaluation.Evaluator;
import akka.eval.contract.evaluation.EvaluatorKind;
// end::imports[]

/**
 * A customer-authored deterministic evaluator for an eval case that requires the reply to
 * name the refund window.
 */
// tag::class[]
public final class RefundWindowEvaluator implements Evaluator {

    private static final String ID = "refund_window_named"; // <1>
    private static final String VERSION = "1";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String version() {
        return VERSION;
    }

    @Override
    public EvaluatorKind kind() {
        return EvaluatorKind.ASSERTION;
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext context) {
        String reply = context.interaction().responseText();

        if (reply == null || reply.isBlank()) { // <2>
            return EvaluationResult.inconclusive(ID, VERSION, EvaluatorKind.ASSERTION,
                "no reply to read");
        }

        boolean stated = reply.matches("(?i).*\\b30[- ]day\\b.*"); // <3>
        return stated
            ? EvaluationResult.passed(ID, VERSION, EvaluatorKind.ASSERTION,
                "30-day refund window stated")
            : EvaluationResult.failed(ID, VERSION, EvaluatorKind.ASSERTION,
                "30-day refund window not stated"); // <4>
    }
}
// end::class[]
