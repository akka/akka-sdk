package com.example.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.client.ComponentClient;

import java.util.UUID;

// tag::prompt[]
@ComponentId("activity-agent")
public class ActivityAgent extends Agent {
  private static final String SYSTEM_MESSAGE = // <1>
      """
      You are an activity agent. Your job is to suggest activities in the
      real world. Like for example, a team building activity, sports, an
      indoor or outdoor game, board games, a city trip, etc.
      """.stripIndent();

  Effect<String> query(String message) {
    return effects()
        .systemMessage(SYSTEM_MESSAGE) // <2>
        .userMessage(message)// <3>
        .thenReply();
  }
}
// end::prompt[]

interface ActivityAgentMore {

  public class Caller {
    private final ComponentClient componentClient;

    public Caller(ComponentClient componentClient) {
      this.componentClient = componentClient;
    }

    public void callIt() {
      // tag::call[]
      var sessionId = UUID.randomUUID().toString();
      String suggestion =
        componentClient
            .forAgent()// <1>
            .inSession(sessionId)// <2>
            .method(ActivityAgent::query)
            .invoke("Business colleagues meeting in London");
      // end::call[]
    }
  }

}
