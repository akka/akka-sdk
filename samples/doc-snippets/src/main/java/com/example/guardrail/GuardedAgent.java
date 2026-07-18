package com.example.guardrail;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.Guardrail;
import akka.javasdk.annotations.Component;

@Component(id = "guarded-agent")
public class GuardedAgent extends Agent {

  public record SomeResponse(String response) {}

  public Effect<SomeResponse> ask(String question) {
    // tag::on-failure[]
    return effects()
      .systemMessage("You are a helpful...")
      .userMessage(question)
      .map(SomeResponse::new)
      .onFailure(cause ->
        switch (cause) {
          case Guardrail.GuardrailException e -> new SomeResponse(e.getMessage());
          default -> throw new RuntimeException(cause);
        })
      .thenReply();
    // end::on-failure[]
  }
}
