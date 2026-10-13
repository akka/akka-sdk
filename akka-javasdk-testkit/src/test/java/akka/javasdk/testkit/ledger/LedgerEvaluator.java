/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.ledger;

import akka.javasdk.evaluation.Evaluation;
import akka.javasdk.evaluation.EvaluationContext;
import akka.javasdk.evaluation.Evaluator;
import akka.javasdk.ledger.LedgerClient;

/**
 * An evaluator that fetches the interaction under evaluation from the ledger and passes if the
 * agent produced any final response text. Used to exercise ledger injection into an evaluator.
 */
public class LedgerEvaluator extends Evaluator {

  private final LedgerClient ledger;

  public LedgerEvaluator(LedgerClient ledger) {
    this.ledger = ledger;
  }

  @Override
  public Effect evaluate(EvaluationContext context) {
    var interaction = ledger.getInteraction(context.subject().interactionId());
    var finalText = interaction.finalResponseText();
    if (finalText.isEmpty()) {
      return effects().complete(Evaluation.failed("agent produced no final response text"));
    }
    return effects()
        .complete(
            Evaluation.passed("agent responded")
                .withAttribute("finalText", finalText)
                .withAttribute("transcript", interaction.transcript()));
  }
}
