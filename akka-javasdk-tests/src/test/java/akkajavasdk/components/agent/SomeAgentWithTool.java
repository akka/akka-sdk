/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.FunctionTool;

@ComponentId("some-agent-with-tool")
public class SomeAgentWithTool extends Agent {

  public class WeatherService {
    @FunctionTool(description = "Returns today's date")
    public String getWeather(String location, String date) {
      return "The weather is sunny in "+location+". (date="+date+")";
    }
  }

  public record SomeResponse(String response) {
  }

  public Effect<SomeResponse> query(String question) {
    return effects()
      .systemMessage("You are a helpful...")
      .tools(new WeatherService())
      .userMessage(question)
      .map(SomeResponse::new)
      .thenReply();
  }

  @FunctionTool(description = "Returns today's date")
  private String getDateOfToday() {
    return "2025-01-01";
  }

}