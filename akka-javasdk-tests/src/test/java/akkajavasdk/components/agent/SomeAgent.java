/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.ModelProvider;
import akka.javasdk.annotations.AgentDescription;
import akka.javasdk.annotations.ComponentId;

/**
 * Dummy agent for testing component auto registration, e.g. PromptTemplate.
 */
@ComponentId("some-agent")
public class SomeAgent extends Agent {
  private final ModelProvider modelProvider;

  public record SomeResponse(String response) {}

  public SomeAgent(ModelProvider modelProvider) {
    this.modelProvider = modelProvider;
  }

  public Effect<SomeResponse> mapLlmResponse(String question) {
    return effects()
      .model(modelProvider)
      .systemMessage("You are a helpful...")
      .userMessage(question)
      .responseAs(String.class)
      .map(SomeResponse::new)
      .thenReply();
  }
}
