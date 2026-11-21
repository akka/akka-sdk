package com.example.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.a2a.A2aSkill;

// tag::skill[]
@Component(
  id = "claim-check-agent",
  name = "Claim checker",
  description = "Checks insurance claims against the policy"
)
@A2aSkill( // <1>
  id = "check-claim",
  name = "Check a claim",
  description = "Checks whether a claim is covered by the policy and explains why",
  tags = { "claims", "insurance" },
  examples = { "Is water damage from a burst pipe covered?" }
)
@A2aSkill(id = "explain-policy", name = "Explain a policy") // <2>
public class ClaimCheckAgent extends Agent {

  public Effect<String> ask(String question) { // <3>
    return effects()
      .systemMessage("You check insurance claims against the policy.")
      .userMessage(question)
      .thenReply();
  }
}
// end::skill[]
