package com.example.application;

import akka.http.javadsl.model.headers.Authorization;
import akka.http.javadsl.model.headers.OAuth2BearerToken;
import akka.javasdk.agent.Agent;
import akka.javasdk.agent.RemoteMcpTools;
import akka.javasdk.annotations.Component;
import java.util.Set;

@Component(id = "remote-mcp-weather-agent")
public class RemoteMcpWeatherAgent extends Agent {

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

  // tag::mcp-function-tool[]
  public Effect<AgentResponse> query(String message) {
    return effects()
      .systemMessage(SYSTEM_MESSAGE)
      .mcpTools(
        RemoteMcpTools.fromService("weather-service"), // <1>
        RemoteMcpTools.fromServer("https://weather.example.com/mcp") // <2>
          .addClientHeader(Authorization.oauth2(System.getenv("WEATHER_API_TOKEN"))) // <3>
          .withAllowedToolNames(Set.of("get_weather")) // <4>
      )
      .userMessage(message)
      .responseAs(AgentResponse.class)
      .thenReply();
  }
  // end::mcp-function-tool[]

}
