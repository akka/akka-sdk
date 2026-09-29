package com.example.evaluator;

// tag::all[]
import akka.javasdk.annotations.Component;
import akka.javasdk.evaluation.Evaluation;
import akka.javasdk.evaluation.EvaluationContext;
import akka.javasdk.evaluation.Evaluator;
import akka.javasdk.ledger.InteractionRecord;
import akka.javasdk.ledger.LedgerClient;

@Component(id = "interaction-quality-evaluator") // <1>
public class InteractionQualityEvaluator extends Evaluator { // <2>

  private final LedgerClient ledger;

  public InteractionQualityEvaluator(LedgerClient ledger) { // <3>
    this.ledger = ledger;
  }

  @Override
  public Effect evaluate(EvaluationContext context) {
    InteractionRecord interaction = ledger.getInteraction(context.subject().interactionId()); // <4>

    if (interaction.failed()) {
      return effects().inconclusive("interaction failed, nothing to evaluate"); // <5>
    }

    String finalText = interaction.finalResponseText();
    if (finalText.isEmpty()) {
      return effects().complete(Evaluation.failed("agent produced no final response")); // <6>
    }
    return effects()
      .complete(
        Evaluation.passed("agent responded")
          .withScore(1.0)
          .withAttribute("length", Integer.toString(finalText.length()))
      ); // <7>
  }
}
// end::all[]
