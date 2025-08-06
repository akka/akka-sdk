/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.ComponentId;


@ComponentId("some-agent-accepting-int")
public class SomeAgentAcceptingInt extends Agent {
  public record SomeResponse(String response) {}

  public Effect<SomeResponse> mapLlmResponse(Integer question) {
    return effects()
      .systemMessage("You are a helpful...")
      .userMessage(question.toString())
      .map(SomeResponse::new)
      .thenReply();
  }
}
