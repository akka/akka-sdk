/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.FunctionTool;

@Component(id = "non-blocking-guardrails-test-agent")
public class NonBlockingGuardrailsTestAgent extends Agent {
  public record SomeResponse(String response) {}

  public Effect<SomeResponse> ask(String question) {
    return effects()
        .systemMessage("You are a helpful assistant")
        .userMessage(question)
        .map(SomeResponse::new)
        .thenReply();
  }

  @FunctionTool(description = "Returns today's date")
  private String getDateOfToday() {
    return "2025-01-01";
  }
}
