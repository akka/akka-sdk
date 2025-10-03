package com.example.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.ComponentId;

@ComponentId("prompt-agent")
public class PromptAgent extends Agent {
  public record Request(String systemMessage, String userMessage) {}

  public Effect<String> send(Request req) {
    return effects()
        .systemMessage(req.systemMessage)
        .userMessage(req.userMessage)
        .thenReply();
  }
}
