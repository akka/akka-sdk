package com.example.eval;

import akka.javasdk.annotations.Component;
import akka.javasdk.evaluation.Evaluation;
import akka.javasdk.evaluation.EvaluationContext;
import akka.javasdk.evaluation.Evaluator;
import akka.javasdk.ledger.LedgerClient;

// tag::all[]
/** A refund goes to an order the agent looked up first. */
@Component(id = "refund-policy")
public class RefundPolicyEvaluator extends Evaluator {

  private final LedgerClient ledger;

  public RefundPolicyEvaluator(LedgerClient ledger) {
    this.ledger = ledger;
  }

  @Override
  public Effect evaluate(EvaluationContext context) {
    var interaction = ledger.getInteraction(context.subject().interactionId());
    if (interaction.toolCalls("issueRefund").isEmpty()) {
      return effects().notApplicable("no refund was issued"); // <1>
    }
    if (interaction.toolCalls("getOrder").isEmpty()) {
      return effects().complete(Evaluation.failed("refunded without looking the order up"));
    }
    return effects().complete(Evaluation.passed("refunded an order the agent looked up"));
  }
}
// end::all[]
