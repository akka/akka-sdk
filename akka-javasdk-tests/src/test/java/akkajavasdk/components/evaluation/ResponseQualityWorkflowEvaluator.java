/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.evaluation;

import static java.time.Duration.ofSeconds;

import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.evaluation.Evaluation;
import akka.javasdk.evaluation.EvaluationContext;
import akka.javasdk.evaluation.Subject;
import akka.javasdk.evaluation.WorkflowEvaluator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A workflow evaluator running a multi-step evaluation: fetch the transcript in one step, judge it
 * with an LLM-as-judge agent in another, and complete with the verdict. Driven by the runtime for
 * each interaction of the agents it is bound to.
 */
@Component(id = "response-quality-workflow-evaluator")
public class ResponseQualityWorkflowEvaluator
    extends WorkflowEvaluator<ResponseQualityWorkflowEvaluator.State> {

  public record State(String transcript) {}

  /**
   * Test probe: the ids of the evaluations this evaluator ran. The runtime generates the id, so
   * this is how a test learns which evaluation to fetch from the ledger; the verdict itself is
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

  public ResponseQualityWorkflowEvaluator(ComponentClient componentClient) {
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
    return effects().transitionTo(ResponseQualityWorkflowEvaluator::fetchTranscript);
  }

  private Effect fetchTranscript() {
    var subject = (Subject.Interaction) evaluationContext().subject();
    // a real evaluator would fetch the transcript via the interaction log
    String transcript =
        "interaction "
            + subject.interactionId()
            + " of agent "
            + subject.agentComponentId().orElse("");
    return effects()
        .updateState(new State(transcript))
        .transitionTo(ResponseQualityWorkflowEvaluator::judge);
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
    evaluationIds.add(evaluationContext().evaluationId());
    return effects().complete(evaluation);
  }
}
