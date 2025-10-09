/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.Guardrail;
import akka.javasdk.annotations.Component;

/** Dummy agent for testing component auto registration, e.g. PromptTemplate. */
@Component(id = "some-agent")
public class SomeAgent extends Agent {
  public record SomeResponse(String response) {}

  public Effect<SomeResponse> mapLlmResponse(String question) {
    return effects()
        .systemMessage("You are a helpful...")
        .userMessage(question)
        .map(SomeResponse::new)
        .onFailure(
            cause -> {
              return switch (cause) {
                case Guardrail.GuardrailException e -> new SomeResponse(e.getMessage());
                case RuntimeException e -> throw e;
                default -> throw new RuntimeException(cause);
              };
            })
        .thenReply();
  }
}
