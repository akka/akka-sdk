package assistant.application;

import akka.Done;
import akka.javasdk.a2a.A2aClient;
import akka.javasdk.a2a.A2aMessage;
import akka.javasdk.a2a.A2aPart;
import akka.javasdk.a2a.A2aSendMessageResponse;
import akka.javasdk.a2a.A2aTaskArtifactUpdate;
import akka.javasdk.a2a.A2aTaskState;
import akka.javasdk.a2a.A2aTaskStatusUpdate;
import akka.javasdk.a2a.A2aTaskUpdate;
import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.client.ComponentMethodRef1;
import akka.javasdk.workflow.Workflow;
import akka.javasdk.workflow.WorkflowContext;
import java.time.Duration;
import java.util.stream.Collectors;

@Component(id = "claim-review-request")
public class ClaimReviewRequestWorkflow extends Workflow<ClaimReviewRequestWorkflow.State> {

  public record State(
    String claimId,
    String policyNumber,
    String remoteTaskId,
    String review,
    String outcome
  ) {
    State withRemoteTask(String taskId) {
      return new State(claimId, policyNumber, taskId, review, outcome);
    }

    State withReview(String text) {
      return new State(claimId, policyNumber, remoteTaskId, text, outcome);
    }

    State withOutcome(String text) {
      return new State(claimId, policyNumber, remoteTaskId, review, text);
    }
  }

  public record Request(String claimId, String policyNumber) {}

  private final A2aClient a2aClient;
  private final ComponentClient componentClient;
  private final WorkflowContext context;

  public ClaimReviewRequestWorkflow(
    A2aClient a2aClient,
    ComponentClient componentClient,
    WorkflowContext context
  ) {
    this.a2aClient = a2aClient;
    this.componentClient = componentClient;
    this.context = context;
  }

  public Effect<Done> start(Request request) {
    return effects()
      .updateState(new State(request.claimId(), request.policyNumber(), null, null, null))
      .transitionTo(ClaimReviewRequestWorkflow::requestReview)
      .thenReply(Done.getInstance());
  }

  public ReadOnlyEffect<State> get() {
    return effects().reply(currentState());
  }

  private StepEffect requestReview() {
    var reply = a2aClient
      .forAgent("claims")
      .send(
        "review-claim",
        A2aMessage.user(
          "Review claim " +
          currentState().claimId() +
          " under policy " +
          currentState().policyNumber()
        )
      )
      .notifyWorkflow(onReviewUpdateReference()) // <1>
      .invoke();

    if (
      reply instanceof A2aSendMessageResponse.Task task &&
      !task.task().status().state().terminal()
    ) {
      return stepEffects()
        .updateState(currentState().withRemoteTask(task.task().id()))
        .thenPause(
          pauseSetting(Duration.ofHours(1)).timeoutHandler(
            ClaimReviewRequestWorkflow::onReviewTimeout
          )
        );
    }
    return stepEffects()
      .updateState(currentState().withOutcome("answered at once"))
      .thenEnd();
  }

  public Effect<Done> onReviewUpdate(A2aTaskUpdate update) { // <2>
    return switch (update) {
      case A2aTaskArtifactUpdate artifact -> effects()
        .updateState(currentState().withReview(text(artifact)))
        .pause()
        .thenReply(Done.getInstance());
      case A2aTaskStatusUpdate status when (
        status.status().state() == A2aTaskState.COMPLETED
      ) -> effects()
        .updateState(currentState().withOutcome("reviewed"))
        .end()
        .thenReply(Done.getInstance());
      case A2aTaskStatusUpdate status when status.status().state().terminal() -> effects()
        .updateState(currentState().withOutcome("review failed"))
        .end()
        .thenReply(Done.getInstance());
      case A2aTaskStatusUpdate status -> effects().reply(Done.getInstance());
    };
  }

  public Effect<Done> onReviewTimeout() {
    return effects()
      .updateState(currentState().withOutcome("no review in time"))
      .end()
      .thenReply(Done.getInstance());
  }

  private ComponentMethodRef1<A2aTaskUpdate, Done> onReviewUpdateReference() {
    return componentClient
      .forWorkflow(context.workflowId())
      .method(ClaimReviewRequestWorkflow::onReviewUpdate);
  }

  private static String text(A2aTaskArtifactUpdate artifact) {
    return artifact
      .artifact()
      .parts()
      .stream()
      .map(part ->
        switch (part) {
          case A2aPart.Text text -> text.text();
          case A2aPart.Data data -> String.valueOf(data.data());
          default -> "";
        })
      .collect(Collectors.joining());
  }
}
