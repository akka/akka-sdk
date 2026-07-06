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
 * assistant produced any final text. Used to exercise ledger injection into an evaluator.
 */
public class LedgerEvaluator extends Evaluator {

  private final LedgerClient ledger;

  public LedgerEvaluator(LedgerClient ledger) {
    this.ledger = ledger;
  }

  @Override
  public Effect evaluate(EvaluationContext context) {
    var interaction = ledger.getInteraction(context.subject().interactionId());
    var finalText = interaction.finalAssistantText();
    if (finalText.isEmpty()) {
      return effects().complete(Evaluation.failed("assistant produced no final text"));
    }
    return effects()
        .complete(
            Evaluation.passed("assistant answered")
                .withAttribute("finalText", finalText)
                .withAttribute("transcript", interaction.transcript()));
  }
}
