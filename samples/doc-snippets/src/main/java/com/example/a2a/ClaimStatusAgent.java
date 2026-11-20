package com.example.a2a;

import akka.javasdk.a2a.RemoteA2aAgent;
import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;
import java.time.Duration;
import java.util.Set;

@Component(id = "claim-status-assistant")
public class ClaimStatusAgent extends Agent {

  public Effect<String> ask(String question) {
    return effects()
      .systemMessage("You tell customers the status of their insurance claims.")
      // tag::skills-and-timeouts[]
      .a2aAgents(
        RemoteA2aAgent.named("claims")
          .withAllowedSkills(Set.of("claim-status")) // <1>
          .withTimeout(Duration.ofSeconds(20)) // <2>
      )
      // end::skills-and-timeouts[]
      .userMessage(question)
      .thenReply();
  }
}
