package com.example.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.a2a.A2aSkill;

// tag::streaming-skill[]
@Component(
  id = "claim-streaming-agent",
  name = "Claim explainer",
  description = "Explains a claim decision"
)
@A2aSkill(id = "explain-claim", name = "Explain a claim decision")
public class ClaimStreamingAgent extends Agent {

  public StreamEffect explain(String question) { // <1>
    return streamEffects()
      .systemMessage("You explain claim decisions.")
      .userMessage(question)
      .thenReply();
  }
}
// end::streaming-skill[]
