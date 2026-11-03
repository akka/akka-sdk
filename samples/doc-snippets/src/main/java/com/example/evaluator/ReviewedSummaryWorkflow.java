package com.example.evaluator;

import static java.time.Duration.ofSeconds;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.workflow.Workflow;

@Component(id = "reviewed-summary")
public class ReviewedSummaryWorkflow extends Workflow<ReviewedSummaryWorkflow.State> {

  public record State(
    String document,
    String summary,
    String feedback,
    int attempts,
    boolean accepted
  ) {
    State withSummary(String newSummary) {
      return new State(document, newSummary, feedback, attempts + 1, false);
    }

    State withFeedback(String newFeedback) {
      return new State(document, summary, newFeedback, attempts, false);
    }

    State asAccepted() {
      return new State(document, summary, feedback, attempts, true);
    }
  }

  private static final int MAX_ATTEMPTS = 3;

  private final ComponentClient componentClient;

  public ReviewedSummaryWorkflow(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Override
  public WorkflowSettings settings() {
    return WorkflowSettings.builder().defaultStepTimeout(ofSeconds(60)).build();
  }

  public Effect<Done> start(String document) {
    return effects()
      .updateState(new State(document, "", "", 0, false))
      .transitionTo(ReviewedSummaryWorkflow::summarize)
      .thenReply(Done.getInstance());
  }

  public Effect<State> get() {
    return effects().reply(currentState());
  }

  private StepEffect summarize() {
    String summary = componentClient
      .forAgent()
      .inSession(commandContext().workflowId())
      .method(DocumentSummarizer::summarize)
      .invoke(
        new DocumentSummarizer.Request(currentState().document(), currentState().feedback())
      );

    return stepEffects()
      .updateState(currentState().withSummary(summary))
      .thenTransitionTo(ReviewedSummaryWorkflow::checkSummary);
  }

  // tag::check[]
  private StepEffect checkSummary() {
    LabelJudge.Verdict verdict = componentClient
      .forAgent()
      .inSession(commandContext().workflowId() + "-label-judge") // <1>
      .method(LabelJudge::judge)
      .invoke(
        new LabelJudge.Request(
          SummarizationJudgeEvaluator.SYSTEM_MESSAGE,
          SummarizationJudgeEvaluator.USER_MESSAGE.formatted(
            currentState().summary(),
            currentState().document()
          )
        )
      ); // <2>

    if ("good".equalsIgnoreCase(verdict.label())) {
      return stepEffects().updateState(currentState().asAccepted()).thenEnd(); // <3>
    } else if (currentState().attempts() < MAX_ATTEMPTS) {
      return stepEffects()
        .updateState(currentState().withFeedback(verdict.explanation()))
        .thenTransitionTo(ReviewedSummaryWorkflow::summarize); // <4>
    } else {
      return stepEffects().thenEnd(); // <5>
    }
  }
  // end::check[]
}
