package com.example.a2a;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.RemoteA2aAgent;
import akka.javasdk.annotations.Component;
import java.time.Duration;

@Component(id = "claim-review-assistant")
public class ClaimReviewAgent extends Agent {

  public Effect<String> ask(String question) {
    return effects()
      .systemMessage("You help claim handlers review insurance claims.")
      // tag::hand-off[]
      .a2aAgents(
        RemoteA2aAgent.named("claims").handOffTasksStillWorkingAfter(Duration.ofSeconds(30)) // <1>
      )
      // end::hand-off[]
      .userMessage(question)
      .thenReply();
  }
}
