/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.ComponentId;

/**
 * Dummy agent for testing component auto registration, e.g. PromptTemplate.
 */
@ComponentId("some-agent")
public class SomeAgent extends Agent {
  public record SomeResponse(String response) {}

  public Effect<SomeResponse> mapLlmResponse(String question) {
    return effects()
      .systemMessage("You are a helpful...")
      .userMessage(question)
      .map(SomeResponse::new)
      .thenReply();
  }
}
