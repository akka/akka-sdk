package com.example.eval;

import akka.javasdk.annotations.Component;
import akka.javasdk.evaluation.Evaluation;
import akka.javasdk.evaluation.EvaluationContext;
import akka.javasdk.evaluation.Evaluator;
import akka.javasdk.evaluation.Subject;
import akka.javasdk.ledger.LedgerClient;
import java.util.List;
import java.util.Locale;

// tag::all[]
/** The reply states no order status other than the one getOrder returned. */
@Component(id = "reply-grounding")
public class ReplyGroundingEvaluator extends Evaluator {

  private static final List<String> STATUSES = List.of(
    "processing",
    "shipped",
    "delivered",
    "cancelled"
  );

  private final LedgerClient ledger;

  public ReplyGroundingEvaluator(LedgerClient ledger) {
    this.ledger = ledger;
  }

  @Override
  public Effect evaluate(EvaluationContext context) {
    var subject = (Subject.Interaction) context.subject();
    var interaction = ledger.getInteraction(subject.interactionId());
    if (interaction.failed()) {
      return effects().inconclusive("the interaction failed, there is no reply to check"); // <1>
    }
    var order = interaction
      .toolCalls(OrderAgent.GET_ORDER)
      .stream()
      .flatMap(call -> call.responseAs(Order.class).stream())
      .findFirst();
    if (order.isEmpty()) {
      return effects().notApplicable("no order was looked up"); // <2>
    }
    var reply = interaction.finalResponseText().toLowerCase(Locale.ROOT);
    var wrong = STATUSES.stream()
      .filter(status -> !status.equals(order.get().status()) && reply.contains(status))
      .toList();
    if (!wrong.isEmpty()) {
      return effects()
        .complete(
          Evaluation.failed(
            "the reply says " + wrong + ", the order is " + order.get().status()
          )
        );
    }
    return effects()
      .complete(
        Evaluation.passed("the reply states no status other than " + order.get().status())
      );
  }
}
// end::all[]
