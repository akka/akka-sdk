/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.AgentRole;
import akka.javasdk.annotations.Component;

/** Dummy agent for testing token streaming. */
@Component(id = "some-streaming-agent", name = "Dummy Agent", description = "Not very smart agent")
@AgentRole("streaming")
public class SomeStreamingAgent extends Agent {

  public StreamEffect ask(String question) {
    return streamEffects().systemMessage("You are a helpful...").userMessage(question).thenReply();
  }
}
