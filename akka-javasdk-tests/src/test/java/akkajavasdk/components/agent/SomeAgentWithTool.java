/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.FunctionTool;

@Component(id = "some-agent-with-tool")
public class SomeAgentWithTool extends Agent {

  public static class WeatherService {
    @FunctionTool(description = "Returns the weather for the passed date")
    public String getWeather(String location, String date) {
      return "The weather is sunny in " + location + ". (date=" + date + ")";
    }
  }

  public static class TrafficService {
    @FunctionTool(description = "Returns the traffic conditions at passed location")
    public String getTrafficNow(String location) {
      return "There is traffic jam in " + location + ".";
    }
  }

  public record SomeResponse(String response) {}

  private boolean constructedOnVt = Thread.currentThread().isVirtual();

  public Effect<SomeResponse> query(String question) {
    if (question.equals("Running on virtual thread?")) {
      return effects()
          .reply(
              new SomeResponse(
                  "Query on vt: "
                      + Thread.currentThread().isVirtual()
                      + ", constructed on vt: "
                      + constructedOnVt));
    }

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
