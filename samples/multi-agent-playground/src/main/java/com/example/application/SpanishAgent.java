package com.example.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;

@Component(
    id = "spanish-agent",
    name = "Spanish Agent",
    description = "An agent that only speaks Spanish.")
public class SpanishAgent extends Agent {

  public Effect<String> reply(String message) {
    return effects()
        .systemMessage("You only speak Spanish.")
        .userMessage(message)
        .thenReply();
  }
}
