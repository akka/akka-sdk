package claims.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Description;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.annotations.a2a.A2aSkill;
import java.util.Map;

@Component(id = "claim-status-agent")
@A2aSkill( // <1>
  id = "claim-status",
  name = "Claim status",
  description = "Tells the status of an insurance claim from its claim number",
  examples = { "What is the status of claim 42?" }
)
public class ClaimStatusAgent extends Agent {

  private static final Map<String, String> STATUS = Map.of(
    "42",
    "approved, payment on its way",
    "43",
    "waiting for photos of the damage"
  );

  public Effect<String> ask(String question) { // <2>
    return effects()
      .systemMessage(
        "You tell policyholders the status of their insurance claims. " +
        "Use the claimStatus tool and answer in one sentence."
      )
      .userMessage(question)
      .thenReply();
  }

  @FunctionTool(description = "Returns the status of a claim given its claim number")
  private String claimStatus(@Description("The claim number") String claimNumber) {
    return STATUS.getOrDefault(claimNumber, "unknown claim");
  }
}
