/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.AgentDescription;
import akka.javasdk.annotations.ComponentId;

/**
 * Dummy agent for testing component auto registration, e.g. PromptTemplate.
 */
@ComponentId("some-agent")
@AgentDescription(name = "Dummy Agent", description = "Not very smart agent")
public class SomeAgent extends Agent {
  public Effect<String> doSomething(String question) {
    return effects()
      .systemMessage("You are a helpful...")
      .userMessage(question)
      .thenReply();
  }
}
