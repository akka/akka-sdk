/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.ComponentId;

@ComponentId("some-agent-with-bad-tool")
public class SomeAgentWithBadlyConfiguredTool extends Agent {

  static public class WeatherServiceWithAnnotation {
    public String getWeather(String location, String date) {
      return "The weather is sunny in "+location+". (date="+date+")";
    }
  }

  public record SomeResponse(String response) {
  }

  public Effect<SomeResponse> query(String question) {
    return effects()
      .systemMessage("You are a helpful...")
      .tools(new WeatherServiceWithAnnotation())
      .userMessage(question)
      .map(SomeResponse::new)
      .thenReply();
  }


}