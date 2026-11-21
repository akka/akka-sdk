package claims.application;

import akka.Done;
import akka.javasdk.a2a.A2aArtifact;
import akka.javasdk.a2a.A2aMessage;
import akka.javasdk.a2a.A2aPart;
import akka.javasdk.a2a.A2aTaskContext;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.a2a.A2aSkill;
import akka.javasdk.workflow.Workflow;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component(id = "claim-review")
@A2aSkill( // <1>
  id = "review-claim",
  name = "Review a claim",
  description = "Reviews a claim. Asks for the policy number when the message has none."
)
public class ClaimReviewWorkflow extends Workflow<ClaimReviewWorkflow.State> {

  public record State(String claim, String policyNumber) {}

  public record Review(String claim, String policyNumber, String decision) {}

  private final A2aTaskContext task;

  public ClaimReviewWorkflow(A2aTaskContext task) { // <2>
    this.task = task;
  }

  public Effect<Done> onMessage(A2aMessage message) { // <3>
    var text = text(message);
    if (currentState() == null) {
      return effects()
        .updateState(new State(text, policyNumberIn(text)))
        .transitionTo(ClaimReviewWorkflow::collectPolicyNumber)
        .thenReply(Done.getInstance());
    }
    return effects()
      .updateState(new State(currentState().claim(), text.trim()))
      .transitionTo(ClaimReviewWorkflow::review)
      .thenReply(Done.getInstance());
  }

  private StepEffect collectPolicyNumber() {
    task.working("Reading the claim"); // <4>
    if (currentState().policyNumber() == null) {
      task.inputRequired("What is the policy number?"); // <5>
      return stepEffects().thenPause();
    }
    return stepEffects().thenTransitionTo(ClaimReviewWorkflow::review);
  }

  private StepEffect review() {
    task.working("Reviewing the claim");
    var state = currentState();
    task.artifact( // <6>
      new A2aArtifact(
        "review",
        "Review",
        null,
        List.of(A2aPart.data(new Review(state.claim(), state.policyNumber(), "approved"))),
        null,
        null
      )
    );
    task.completed("The claim is approved."); // <7>
    return stepEffects().thenEnd();
  }

  private static final Pattern POLICY_NUMBER = Pattern.compile("\\bP-\\d+\\b");

  private static String policyNumberIn(String text) {
    var matcher = POLICY_NUMBER.matcher(text);
    return matcher.find() ? matcher.group() : null;
  }

  private static String text(A2aMessage message) {
    return message
      .parts()
      .stream()
      .filter(part -> part instanceof A2aPart.Text)
      .map(part -> ((A2aPart.Text) part).text())
      .collect(Collectors.joining("\n"));
  }
}
