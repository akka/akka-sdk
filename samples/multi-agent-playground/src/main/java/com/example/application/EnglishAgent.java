package com.example.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;

@Component(
    id = "english-agent",
    name = "English Agent",
    description = "An agent that only speaks English.")
public class EnglishAgent extends Agent {

  public Effect<String> reply(String message) {
    return effects()
        .systemMessage("You only speak English.")
        .userMessage(message)
        .thenReply();
  }
}
