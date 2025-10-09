/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.FunctionTool;

@Component(id = "some-agent-with-failing-tool")
public class SomeAgentWithFailingTool extends Agent {

  public static class TrafficService {
    public TrafficService() {
      throw new RuntimeException("Failed to instantiate TrafficService");
    }

    @FunctionTool(description = "Returns the traffic conditions at passed location")
    public String getTrafficNow(String location) {
      return "There is traffic jam in " + location + ".";
    }
  }

  public record SomeResponse(String response) {}

  public Effect<SomeResponse> query(String question) {
    return effects()
        .systemMessage("You are a helpful...")
        .tools(TrafficService.class)
        .userMessage(question)
        .map(SomeResponse::new)
        .thenReply();
  }
}
