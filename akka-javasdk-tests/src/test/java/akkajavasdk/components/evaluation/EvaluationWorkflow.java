/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.evaluation;

import akka.javasdk.NotificationPublisher;
import akka.javasdk.NotificationPublisher.NotificationStream;
import akka.javasdk.annotations.Component;
import akka.javasdk.workflow.Workflow;

/**
 * A multi-step evaluation workflow used by the workflow-evaluator integration test. It scores a
 * transcript in one step, finalizes in another, and publishes the completed report as a
 * notification that subscribers (the evaluator) can await without polling.
 */
@Component(id = "evaluation-workflow")
public class EvaluationWorkflow extends Workflow<EvaluationWorkflow.State> {

  public record State(String transcript, int score) {
    State scored(int newScore) {
      return new State(transcript, newScore);
    }
  }

  public record Report(int score, String reason) {}

  private final NotificationPublisher<Report> notificationPublisher;

  public EvaluationWorkflow(NotificationPublisher<Report> notificationPublisher) {
    this.notificationPublisher = notificationPublisher;
  }

  public Effect<String> run(String transcript) {
    return effects()
        .updateState(new State(transcript, 0))
        .transitionTo(EvaluationWorkflow::scoreStep)
        .thenReply("started");
  }

  private StepEffect scoreStep() {
    int score = currentState().transcript().length();
    return stepEffects()
        .updateState(currentState().scored(score))
        .thenTransitionTo(EvaluationWorkflow::finishStep);
  }

  private StepEffect finishStep() {
    var state = currentState();
    notificationPublisher.publish(new Report(state.score(), "scored " + state.score()));
    return stepEffects().thenEnd();
  }

  public NotificationStream<Report> updates() {
    return notificationPublisher.stream();
  }
}
