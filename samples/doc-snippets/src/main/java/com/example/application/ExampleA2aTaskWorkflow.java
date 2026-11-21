package com.example.application;

import akka.Done;
import akka.javasdk.a2a.A2aArtifact;
import akka.javasdk.a2a.A2aMessage;
import akka.javasdk.a2a.A2aPart;
import akka.javasdk.a2a.A2aTaskContext;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.a2a.A2aSkill;
import akka.javasdk.workflow.Workflow;
import java.util.List;
import java.util.stream.Collectors;

// tag::task-workflow[]
@Component(id = "claim-review")
@A2aSkill( // <1>
  id = "review-claim",
  name = "Review a claim",
  description = "Reviews a claim and asks for the policy number when it is missing"
)
public class ExampleA2aTaskWorkflow extends Workflow<ExampleA2aTaskWorkflow.State> {

  public record State(String claim, String policyNumber) {}

  private final A2aTaskContext task;

  public ExampleA2aTaskWorkflow(A2aTaskContext task) { // <2>
    this.task = task;
  }

  public Effect<Done> onMessage(A2aMessage message) { // <3>
    var text = text(message);
    if (currentState() == null) {
      return effects()
        .updateState(new State(text, null))
        .transitionTo(ExampleA2aTaskWorkflow::checkClaim)
        .thenReply(Done.getInstance());
    }
    return effects()
      .updateState(new State(currentState().claim(), text))
      .transitionTo(ExampleA2aTaskWorkflow::review)
      .thenReply(Done.getInstance());
  }

  private StepEffect checkClaim() {
    task.working("Checking the claim"); // <4>
    if (!currentState().claim().contains("policy")) {
      task.inputRequired("What is the policy number?"); // <5>
      return stepEffects().thenPause();
    }
    return stepEffects().thenTransitionTo(ExampleA2aTaskWorkflow::review);
  }

  private StepEffect review() {
    task.artifact( // <6>
      new A2aArtifact(
        "review",
        "Review",
        null,
        List.of(A2aPart.data(new Review(currentState().claim(), "approved"))),
        null,
        null
      )
    );
    task.completed("The claim is approved."); // <7>
    return stepEffects().thenEnd();
  }

  // end::task-workflow[]
  public record Review(String claim, String decision) {}

  private static String text(A2aMessage message) {
    return message
      .parts()
      .stream()
      .filter(part -> part instanceof A2aPart.Text)
      .map(part -> ((A2aPart.Text) part).text())
      .collect(Collectors.joining("\n"));
  }
  // tag::task-workflow[]
}
// end::task-workflow[]
