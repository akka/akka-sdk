/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.evaluation;

import akka.javasdk.client.ComponentClient;
import akka.javasdk.evaluation.Evaluation;
import akka.javasdk.evaluation.EvaluationContext;
import akka.javasdk.evaluation.Evaluator;

/**
 * An evaluator that delegates to a judge agent (LLM-as-judge) via the component client, in an
 * evaluation-scoped session, and records the verdict.
 */
public class ConversationQualityEvaluator extends Evaluator {

  private final ComponentClient componentClient;

  public ConversationQualityEvaluator(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Override
  public Effect evaluate(EvaluationContext context) {
    var subject = context.subject();
    // a real evaluator would fetch the transcript via the interaction log / ledger client
    String transcript =
        "interaction " + subject.interactionId() + " of agent " + subject.agentComponentId();

    // run the judge in its own session, derived from the evaluation id and isolated from the
    // subject's session
    QualityJudge.Verdict verdict =
        componentClient
            .forAgent()
            .inSession(context.evaluationId() + "-quality-judge")
            .method(QualityJudge::evaluate)
            .invoke(transcript);

    return effects()
        .complete(Evaluation.of(verdict.passed(), verdict.reason()).withScore(verdict.score()));
  }
}
