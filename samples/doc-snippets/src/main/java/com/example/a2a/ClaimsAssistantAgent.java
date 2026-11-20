package com.example.a2a;

import akka.javasdk.a2a.RemoteA2aAgent;
import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;

// tag::a2a-agents[]
@Component(id = "claims-assistant")
public class ClaimsAssistantAgent extends Agent {

  public Effect<String> ask(String question) {
    return effects()
      .systemMessage("You help customers with their insurance claims.")
      .a2aAgents( // <1>
        RemoteA2aAgent.named("claims"), // <2>
        RemoteA2aAgent.fromCard("https://rates.example.com/.well-known/agent-card.json") // <3>
      )
      .userMessage(question)
      .thenReply();
  }
}
// end::a2a-agents[]
