/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.Guardrail;
import akka.javasdk.annotations.Component;

@Component(id = "throwing-guardrail-test-agent")
public class ThrowingGuardrailTestAgent extends Agent {
  public record SomeResponse(String response, String cause) {}

  public Effect<SomeResponse> ask(String question) {
    return effects()
        .systemMessage("You are a helpful...")
        .userMessage(question)
        .map(reply -> new SomeResponse(reply, null))
        .onFailure(
            cause -> {
              return switch (cause) {
                case Guardrail.GuardrailException e ->
                    new SomeResponse(e.getMessage(), String.valueOf(e.getCause()));
                case RuntimeException e -> throw e;
                default -> throw new RuntimeException(cause);
              };
            })
        .thenReply();
  }
}
