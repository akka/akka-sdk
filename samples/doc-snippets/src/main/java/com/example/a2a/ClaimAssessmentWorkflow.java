package com.example.a2a;

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
import akka.javasdk.workflow.Workflow;
import akka.javasdk.workflow.WorkflowContext;
import java.time.Duration;
import java.util.stream.Collectors;

@Component(id = "claim-assessment")
public class ClaimAssessmentWorkflow extends Workflow<ClaimAssessmentWorkflow.State> {

  public record State(
    String claimId,
    String remoteTaskId,
    String assessment,
    String outcome
  ) {
    State withRemoteTask(String taskId) {
      return new State(claimId, taskId, assessment, outcome);
    }

    State withAssessment(String text) {
      return new State(claimId, remoteTaskId, text, outcome);
    }

    State withOutcome(String text) {
      return new State(claimId, remoteTaskId, assessment, text);
    }
  }

  private final A2aClient a2aClient;
  private final ComponentClient componentClient;
  private final WorkflowContext context;

  public ClaimAssessmentWorkflow(
    A2aClient a2aClient,
    ComponentClient componentClient,
    WorkflowContext context
  ) {
    this.a2aClient = a2aClient;
    this.componentClient = componentClient;
    this.context = context;
  }

  public Effect<Done> start(String claimId) {
    return effects()
      .updateState(new State(claimId, null, null, null))
      .transitionTo(ClaimAssessmentWorkflow::requestAssessment)
      .thenReply(Done.getInstance());
  }

  // tag::send[]
  private StepEffect requestAssessment() {
    var reply = a2aClient
      .forAgent("claims")
      .send("assess-claim", A2aMessage.user("Assess claim " + currentState().claimId()))
      .notifyWorkflow( // <1>
        componentClient
          .forWorkflow(context.workflowId())
          .method(ClaimAssessmentWorkflow::onAssessmentUpdate)
      )
      .invoke(); // <2>

    if (
      reply instanceof A2aSendMessageResponse.Task task &&
      !task.task().status().state().terminal()
    ) {
      return stepEffects()
        .updateState(currentState().withRemoteTask(task.task().id()))
        .thenPause(
          pauseSetting(Duration.ofHours(4)).timeoutHandler( // <3>
            ClaimAssessmentWorkflow::onAssessmentTimeout
          )
        );
    }
    return stepEffects()
      .updateState(currentState().withOutcome("answered at once"))
      .thenEnd();
  }

  // end::send[]

  // tag::update[]
  public Effect<Done> onAssessmentUpdate(A2aTaskUpdate update) { // <1>
    return switch (update) {
      case A2aTaskArtifactUpdate artifact -> effects() // <2>
        .updateState(currentState().withAssessment(text(artifact)))
        .pause()
        .thenReply(Done.getInstance());
      case A2aTaskStatusUpdate status when (
        status.status().state() == A2aTaskState.COMPLETED
      ) -> effects() // <3>
        .updateState(currentState().withOutcome("assessed"))
        .end()
        .thenReply(Done.getInstance());
      case A2aTaskStatusUpdate status when status.status().state().terminal() -> effects()
        .updateState(currentState().withOutcome("assessment failed"))
        .end()
        .thenReply(Done.getInstance());
      case A2aTaskStatusUpdate status -> effects().reply(Done.getInstance()); // <4>
    };
  }

  public Effect<Done> onAssessmentTimeout() { // <5>
    return effects()
      .updateState(currentState().withOutcome("no assessment in time"))
      .end()
      .thenReply(Done.getInstance());
  }

  // end::update[]

  private static String text(A2aTaskArtifactUpdate artifact) {
    return artifact
      .artifact()
      .parts()
      .stream()
      .map(part -> part instanceof A2aPart.Text text ? text.text() : "")
      .collect(Collectors.joining());
  }
}
