package com.example.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.FunctionTool;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@ComponentId("lazy-weather-agent")
public class LazyWeatherAgent extends Agent {

  record AgentResponse() {}

  private static final String SYSTEM_MESSAGE =
    """
    You are a weather agent.
    Your job is to provide weather information.
    You provide current weather, forecasts, and other related information.

    The responses from the weather services are in json format. You need to
    digest it into human language. Be aware that Celsius temperature is in
    temp_c field. Fahrenheit temperature is in temp_f field.
    """.stripIndent();

  // tag::function-tool[]
  public Effect<AgentResponse> query(String message) {
    return effects()
      .systemMessage(SYSTEM_MESSAGE)
      .tools(WeatherService.class) // <1>
      .userMessage(message)
      .responseAs(AgentResponse.class)
      .thenReply();
  }
  // end::function-tool[]

}
