package com.example.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.AgentRole;
import akka.javasdk.annotations.Component;

@Component(
  id = "activity-agent",
  name = "Activity Agent",
  description = """
    An agent that suggests activities in the real world. Like for example,
    a team building activity, sports, an indoor or outdoor game,
    board games, a city trip, etc.
  """
)
@AgentRole("worker")
public class ActivityAgent extends Agent {

  private static final String SYSTEM_MESSAGE =
    """
      You are an activity agent. Your job is to suggest activities in the real world.
      Like for example, a team building activity, sports, an indoor or outdoor game,
      board games, a city trip, etc.

      IMPORTANT:
      You return an error if the asked question is outside your domain of expertise,
      if it's invalid or if you cannot provide a response for any other reason.
      Start the error response with ERROR.
    """.stripIndent();

  public Effect<String> query(String request) {
    return effects().systemMessage(SYSTEM_MESSAGE).userMessage(request).thenReply();
  }
}
