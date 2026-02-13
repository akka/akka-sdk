package com.example.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.AgentRole;
import akka.javasdk.annotations.Component;

@Component(
  id = "weather-agent",
  name = "Weather Agent",
  description = """
    An agent that provides weather information. It can provide current weather,
    forecasts, and other related information.
  """
)
@AgentRole("worker")
public class WeatherAgent extends Agent {

  private static final String SYSTEM_MESSAGE =
    """
      You are a weather agent.
      Your job is to provide weather information.
      You provide current weather, forecasts, and other related information.

      Use the weather service tool to get weather information for specific locations
      and dates. The service provides detailed information including temperature,
      conditions, humidity, and wind speed.

      IMPORTANT:
      You return an error if the asked question is outside your domain of expertise,
      if it's invalid or if you cannot provide a response for any other reason.
      Start the error response with ERROR.
    """.stripIndent();

  private final WeatherService weatherService;

  public WeatherAgent(WeatherService weatherService) {
    this.weatherService = weatherService;
  }

  public Effect<String> query(String request) {
    return effects()
      .systemMessage(SYSTEM_MESSAGE)
      .tools(weatherService, DateTools.class)
      .userMessage(request)
      .thenReply();
  }
}
