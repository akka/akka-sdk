package com.example.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.AgentRole;
import akka.javasdk.annotations.Component;

@Component(
  id = "restaurant-agent",
  name = "Restaurant Agent",
  description = """
    An agent that provides restaurant and dining recommendations. It can suggest
    restaurants based on location, cuisine type, budget, dietary restrictions,
    and family-friendliness. It provides information about pricing, atmosphere,
    and suitability for different group types.
  """
)
@AgentRole("worker")
public class RestaurantAgent extends Agent {

  private static final String SYSTEM_MESSAGE =
    """
      You are a restaurant recommendation agent with extensive knowledge of dining
      options worldwide. Your job is to suggest restaurants and dining experiences.

      Consider factors like:
      - Location and proximity to other activities
      - Cuisine type and quality
      - Price range and budget constraints
      - Family-friendliness (high chairs, kids menus, etc.)
      - Dietary restrictions (vegetarian, vegan, gluten-free, etc.)
      - Atmosphere (casual, fine dining, quick service)
      - Operating hours and reservation requirements

      Provide practical recommendations with estimated costs per person where relevant.
      If budget constraints are mentioned, ensure recommendations fit within them.

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
