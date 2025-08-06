package com.example.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.ComponentId;

@ComponentId("weather-agent")
public class WeatherAgent extends Agent {

  private static final String SYSTEM_MESSAGE =
    """
    You are a weather agent.
    Your job is to provide weather information.
    You provide current weather, forecasts, and other related information.

    The responses from the weather services are in json format. You need to digest
    it into human language. Be aware that Celsius temperature is in temp_c field.
    Fahrenheit temperature is in temp_f field.
    """.stripIndent();

  public Effect<String> query(String message) {
    return effects().systemMessage(SYSTEM_MESSAGE).userMessage(message).thenReply();
  }
}
