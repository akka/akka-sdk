/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.a2a;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;

@Component(
    id = "a2a-streaming-agent",
    name = "Streaming agent",
    description = "Answers with a stream of tokens")
public class A2aStreamingAgent extends Agent {

  public StreamEffect ask(String question) {
    return streamEffects()
        .systemMessage("You are a helpful assistant.")
        .userMessage(question)
        .thenReply();
  }
}
