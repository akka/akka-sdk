/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.evaluation;

import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.evaluation.Evaluation;
import akka.javasdk.evaluation.EvaluationContext;
import akka.javasdk.evaluation.Evaluator;
import akka.javasdk.ledger.LedgerClient;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A stateless evaluator: fetches the interaction under evaluation from the ledger and judges its
 * transcript with an LLM-as-judge agent, in a single handler. Driven by the runtime for each
 * interaction of the agents it is bound to.
 */
@Component(id = "response-quality-evaluator")
public class ResponseQualityEvaluator extends Evaluator {

  /**
   * Test probe: the ids of the evaluations this evaluator ran. The runtime generates the id, so
   * this is how a test learns which evaluation to fetch from the ledger; the outcome itself is
   * asserted on the recorded evaluation, not here.
   */
  private static final Set<String> evaluationIds = ConcurrentHashMap.newKeySet();

  public static Set<String> evaluationIds() {
    return evaluationIds;
  }

  public static void clearEvaluationIds() {
    evaluationIds.clear();
  }

  private final ComponentClient componentClient;
  private final LedgerClient ledger;

  public ResponseQualityEvaluator(ComponentClient componentClient, LedgerClient ledger) {
    this.componentClient = componentClient;
    this.ledger = ledger;
  }

  @Override
  public Effect evaluate(EvaluationContext context) {
    // recorded before judging, so a test can find the evaluation even when the judge call fails
    evaluationIds.add(context.evaluationId());

    var interaction = ledger.getInteraction(context.subject().interactionId());

    // run the judge in its own session, derived from the evaluation id and isolated from the
    // subject's session
    QualityJudge.Verdict verdict =
        componentClient
            .forAgent()
            .inSession(context.evaluationId() + "-judge")
            .method(QualityJudge::evaluate)
            .invoke(interaction.transcript());

    if (verdict.reason() == null || verdict.reason().isBlank()) {
      return effects().inconclusive("judge returned no reason for its verdict");
    }
    return effects()
        .complete(
            Evaluation.of(verdict.passed(), verdict.reason())
                .withScore(verdict.score())
                .withAttribute("finalText", interaction.finalResponseText()));
  }
}
