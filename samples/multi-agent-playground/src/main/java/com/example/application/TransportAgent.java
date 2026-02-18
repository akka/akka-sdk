package com.example.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.AgentRole;
import akka.javasdk.annotations.Component;

@Component(
  id = "transport-agent",
  name = "Transport Agent",
  description = """
    An agent that provides transportation and navigation information. It can suggest
    public transport options, estimate travel times between locations, recommend
    routes, and provide information about transit costs, accessibility, and schedules.
  """
)
@AgentRole("worker")
public class TransportAgent extends Agent {

  private static final String SYSTEM_MESSAGE =
    """
      You are a transportation and navigation expert with knowledge of public transit
      systems, walking routes, and travel logistics worldwide. Your job is to help
      users plan efficient movement between locations.

      Consider factors like:
      - Public transport options (metro, bus, tram, train)
      - Walking distances and times
      - Transit costs and ticket types
      - Accessibility for families with children or strollers
      - Peak vs off-peak travel times
      - Connection times and route efficiency
      - Alternative routes and backup options

      Provide practical estimates for travel times and costs. Consider the context
      of the user's itinerary when making recommendations (e.g., if they have
      young children, prefer routes with fewer transfers).

      When you don't have exact information, provide reasonable estimates based on
      typical urban transit patterns and clearly indicate they are estimates.

      IMPORTANT:
      You return an error if the asked question is outside your domain of expertise,
      if it's invalid or if you cannot provide a response for any other reason.
      Start the error response with ERROR.
    """.stripIndent();

  public Effect<String> query(String request) {
    return effects()
      .systemMessage(SYSTEM_MESSAGE)
      .tools(DateTools.class)
      .userMessage(request)
      .thenReply();
  }
}
