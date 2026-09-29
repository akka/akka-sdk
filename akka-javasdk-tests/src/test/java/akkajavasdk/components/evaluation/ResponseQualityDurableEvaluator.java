/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.evaluation;

import static java.time.Duration.ofSeconds;

import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.evaluation.DurableEvaluator;
import akka.javasdk.evaluation.Evaluation;
import akka.javasdk.evaluation.EvaluationContext;

/**
 * A durable evaluator running a multi-step evaluation: fetch the transcript in one step, judge it
 * with an LLM-as-judge agent in another, and complete with the verdict. Driven by the runtime for
 * each interaction of the agents it is bound to.
 */
@Component(id = "response-quality-durable-evaluator")
public class ResponseQualityDurableEvaluator
    extends DurableEvaluator<ResponseQualityDurableEvaluator.State> {

  public record State(String transcript) {}

  private final ComponentClient componentClient;

  public ResponseQualityDurableEvaluator(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Override
  public Settings settings() {
    return Settings.defaults()
        .withEvaluationTimeout(ofSeconds(10))
        .withDefaultStepTimeout(ofSeconds(5))
        .withMaxStepRetries(2);
  }

  @Override
  public Effect onEvaluation(EvaluationContext context) {
    return effects().transitionTo(ResponseQualityDurableEvaluator::fetchTranscript);
  }

  private Effect fetchTranscript() {
    var subject = evaluationContext().subject();
    // a real evaluator would fetch the transcript via the interaction log
    String transcript =
        "interaction " + subject.interactionId() + " of agent " + subject.agentComponentId();
    return effects()
        .updateState(new State(transcript))
        .transitionTo(ResponseQualityDurableEvaluator::judge);
  }

  private Effect judge() {
    // run the judge in its own session, derived from the evaluation id and isolated from the
    // subject's session
    QualityJudge.Verdict verdict =
        componentClient
            .forAgent()
            .inSession(evaluationContext().evaluationId() + "-judge")
            .method(QualityJudge::evaluate)
            .invoke(currentState().transcript());

    var evaluation = Evaluation.of(verdict.passed(), verdict.reason()).withScore(verdict.score());
    return effects().complete(evaluation);
  }
}
