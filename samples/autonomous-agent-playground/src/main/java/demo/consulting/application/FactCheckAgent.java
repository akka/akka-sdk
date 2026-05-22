package demo.consulting.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;

@Component(
  id = "fact-check-agent",
  description = "Verifies factual claims and provides confidence scores"
)
public class FactCheckAgent extends Agent {

  public record FactCheckResult(boolean verified, int confidence, String explanation) {}

  public Effect<FactCheckResult> checkFacts(String claim) {
    return effects()
      .systemMessage(
        "You are a fact checker. Verify the claim and provide a confidence score 0-100."
      )
      .userMessage(claim)
      .responseConformsTo(FactCheckResult.class)
      .thenReply();
  }
}
