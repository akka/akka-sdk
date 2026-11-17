package com.example.eval;

import akka.javasdk.annotations.Component;
import akka.javasdk.evaluation.Evaluation;
import akka.javasdk.evaluation.EvaluationContext;
import akka.javasdk.evaluation.Evaluator;
import akka.javasdk.evaluation.Subject;
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
    var subject = (Subject.Interaction) context.subject();
    var interaction = ledger.getInteraction(subject.interactionId());
    var issued = interaction
      .toolCalls(OrderAgent.ISSUE_REFUND)
      .stream()
      .anyMatch(call -> !call.response().isEmpty()); // <1>
    if (!issued) {
      return effects().notApplicable("no refund was issued"); // <2>
    }
    if (interaction.toolCalls(OrderAgent.GET_ORDER).isEmpty()) {
      return effects().complete(Evaluation.failed("refunded without looking the order up"));
    }
    return effects().complete(Evaluation.passed("refunded an order the agent looked up"));
  }
}
// end::all[]
