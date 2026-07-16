package com.example.evaluator;

import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.evaluation.Evaluation;
import akka.javasdk.evaluation.EvaluationContext;
import akka.javasdk.evaluation.Evaluator;

@Component(id = "judge-delegating-evaluator")
public class JudgeDelegatingEvaluator extends Evaluator {

  private final ComponentClient componentClient;

  public JudgeDelegatingEvaluator(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Override
  public Effect evaluate(EvaluationContext context) {
    String transcript = context.subject().interactionId();
    // tag::delegate[]
    QualityJudge.Verdict verdict = componentClient
        .forAgent()
        .inSession(context.evaluationId() + "-quality-judge") // isolated from the subject's session
        .method(QualityJudge::evaluate)
        .invoke(transcript);

    return effects().complete(
        Evaluation.of(verdict.passed(), verdict.reason()).withScore(verdict.score()));
    // end::delegate[]
  }
}
