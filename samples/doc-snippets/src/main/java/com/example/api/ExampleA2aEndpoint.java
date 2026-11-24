package com.example.api;

import akka.javasdk.a2a.A2aMessage;
import akka.javasdk.a2a.A2aReply;
import akka.javasdk.a2a.AbstractA2aEndpoint;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.a2a.A2aEndpoint;
import akka.javasdk.annotations.a2a.A2aSkill;
import akka.javasdk.client.ComponentClient;
import com.example.application.WeatherAgent;

// tag::endpoint[]
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
@A2aEndpoint(
  path = "/a2a/weather", // <1>
  name = "Weather",
  description = "Answers questions about the weather",
  version = "1.0.0",
  skills = {
    @A2aSkill( // <2>
      id = "forecast",
      name = "Forecast",
      description = "Tells the weather forecast for a city",
      examples = { "What is the weather in Stockholm?" }
    ),
  }
)
public class ExampleA2aEndpoint extends AbstractA2aEndpoint {

  private final ComponentClient componentClient;

  public ExampleA2aEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Override
  public A2aReply onMessage(A2aMessage message) { // <3>
    var answer = componentClient
      .forAgent()
      .inSession(requestContext().sessionId()) // <4>
      .method(WeatherAgent::query)
      .invoke(message.text());
    return A2aReply.text(answer);
  }
}
// end::endpoint[]
