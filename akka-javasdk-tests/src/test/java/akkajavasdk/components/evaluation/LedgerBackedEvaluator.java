/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.evaluation;

import akka.javasdk.annotations.Component;
import akka.javasdk.evaluation.Evaluation;
import akka.javasdk.evaluation.EvaluationContext;
import akka.javasdk.evaluation.Evaluator;
import akka.javasdk.ledger.InteractionRecord;
import akka.javasdk.ledger.LedgerClient;

/**
 * An evaluator that fetches the interaction under evaluation from the real {@link LedgerClient}
 * injected by the runtime, records it into {@link LedgerEvalProbe}, and passes if the assistant
 * produced a final answer. Bound to {@link LedgerEvalAgent} via {@code akka.javasdk.evaluation}
 * config so the runtime triggers it for each of that agent's interactions.
 */
@Component(id = "ledger-eval-evaluator")
public class LedgerBackedEvaluator extends Evaluator {

  private final LedgerClient ledger;

  public LedgerBackedEvaluator(LedgerClient ledger) {
    this.ledger = ledger;
  }

  @Override
  public Effect evaluate(EvaluationContext context) {
    InteractionRecord interaction = ledger.getInteraction(context.subject().interactionId());
    LedgerEvalProbe.record(interaction);

    String finalText = interaction.finalAssistantText();
    if (finalText.isEmpty()) {
      return effects().complete(Evaluation.failed("assistant produced no final text"));
    }
    return effects()
        .complete(Evaluation.passed("assistant answered").withAttribute("finalText", finalText));
  }
}
