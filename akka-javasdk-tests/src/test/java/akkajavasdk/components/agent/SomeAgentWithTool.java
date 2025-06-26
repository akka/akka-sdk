/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.FunctionTool;

@ComponentId("some-agent-with-tool")
public class SomeAgentWithTool extends Agent {

  static public class WeatherService {
    @FunctionTool(description = "Returns the weather for the passed date")
    public String getWeather(String location, String date) {
      return "The weather is sunny in "+location+". (date="+date+")";
    }
  }

  static public class TrafficService {
    @FunctionTool(description = "Returns the traffic conditions at passed location")
    public String getTrafficNow(String location) {
      return "There is traffic jam in "+location + ".";
    }
  }

  public record SomeResponse(String response) {
  }

  public Effect<SomeResponse> query(String question) {
    return effects()
      .systemMessage("You are a helpful...")
      .tools(new WeatherService(), TrafficService.class)
      .userMessage(question)
      .map(SomeResponse::new)
      .thenReply();
  }


  @FunctionTool(description = "Returns today's date")
  private String getDateOfToday() {
    return "2025-01-01";
  }

}