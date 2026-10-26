package com.example.eval;

import akka.javasdk.annotations.Component;
import akka.javasdk.evaluation.Evaluation;
import akka.javasdk.evaluation.EvaluationContext;
import akka.javasdk.evaluation.Evaluator;
import akka.javasdk.ledger.LedgerClient;
import java.util.regex.Pattern;

// tag::all[]
/** The agent looks an order up when the message names one, and calls no tool otherwise. */
@Component(id = "order-lookup") // <1>
public class OrderLookupEvaluator extends Evaluator {

  private static final Pattern ORDER_ID = Pattern.compile("o_\\d+");

  private final LedgerClient ledger;

  public OrderLookupEvaluator(LedgerClient ledger) { // <2>
    this.ledger = ledger;
  }

  @Override
  public Effect evaluate(EvaluationContext context) {
    var interaction = ledger.getInteraction(context.subject().interactionId()); // <3>
    if (interaction.failed() && interaction.toolCalls().isEmpty()) {
      return effects().inconclusive("the interaction failed before any tool call"); // <4>
    }
    var namesAnOrder = ORDER_ID.matcher(interaction.inputText()).find();
    var lookedUp = !interaction.toolCalls("getOrder").isEmpty();
    if (namesAnOrder && !lookedUp) {
      return effects()
        .complete(Evaluation.failed("no getOrder call for a message that names an order")); // <5>
    }
    if (!namesAnOrder && !interaction.toolCalls().isEmpty()) {
      return effects()
        .complete(
          Evaluation.failed(
            "called " + interaction.toolNames() + " for a message without an order id"
          )
        );
    }
    return effects()
      .complete(
        Evaluation.passed(
          namesAnOrder ? "looked the order up" : "no tool for a message without an order id"
        )
      ); // <6>
  }
}
// end::all[]
