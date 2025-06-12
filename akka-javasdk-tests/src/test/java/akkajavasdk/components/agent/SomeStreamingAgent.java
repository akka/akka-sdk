/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.ModelProvider;
import akka.javasdk.annotations.AgentDescription;
import akka.javasdk.annotations.ComponentId;

/**
 * Dummy agent for testing token streaming.
 */
@ComponentId("some-streaming-agent")
@AgentDescription(name = "Dummy Agent", description = "Not very smart agent", role = "streaming")
public class SomeStreamingAgent extends Agent {
  private final ModelProvider modelProvider;

  public SomeStreamingAgent(ModelProvider modelProvider) {
    this.modelProvider = modelProvider;
  }

  public StreamEffect ask(String question) {
    return streamEffects()
      .model(modelProvider)
      .systemMessage("You are a helpful...")
      .userMessage(question)
      .thenReply();
  }
}
