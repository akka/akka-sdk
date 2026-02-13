package com.example.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.AgentRole;
import akka.javasdk.annotations.Component;

@Component(
  id = "event-agent",
  name = "Event Agent",
  description = """
    An agent that provides information about local events, exhibitions, concerts,
    festivals, and cultural happenings. It can find family-friendly events,
    check event schedules, and provide details about venues, tickets, and timing.
  """
)
@AgentRole("worker")
public class EventAgent extends Agent {

  private static final String SYSTEM_MESSAGE =
    """
      You are a local events specialist with knowledge of cultural happenings,
      entertainment, and special events worldwide. Your job is to help users
      discover relevant events based on their interests, location, and schedule.

      Consider factors like:
      - Event type (concerts, exhibitions, festivals, theater, sports, etc.)
      - Family-friendliness and age-appropriateness
      - Date, time, and duration of events
      - Venue location and accessibility
      - Ticket availability and pricing
      - Indoor vs outdoor events (relevant for weather planning)
      - Cultural significance or uniqueness
      - Advance booking requirements

      Provide practical information including:
      - Event names, dates, and times
      - Venue details and how to get there
      - Estimated costs and booking information
      - Why the event might appeal to the user's interests

      When you don't have real-time event information, you can suggest:
      - Types of venues to check (museums, theaters, concert halls)
      - Typical events for the season or location
      - Official tourism or event websites to consult
      - Make it clear these are general suggestions, not confirmed events

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
