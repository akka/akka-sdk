package com.example.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.AgentTeam;
import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Activity planner agent that orchestrates multiple specialist agents
 * to plan activities, check weather, find restaurants, plan transport,
 * discover events, and track budget.
 */
@Component(id = "activity-planner-agent")
public class ActivityPlannerAgent extends Agent implements AgentTeam<String, String> {

  private static final Logger logger = LoggerFactory.getLogger(ActivityPlannerAgent.class);

  private final ComponentClient componentClient;

  public ActivityPlannerAgent(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Override
  public String instructions() {
    return """
    You are an activity planner coordinator. Your job is to help the user plan \
    activities by gathering information from specialist agents.

    For each user request, consider:
    1. Check the weather forecast for the relevant location and dates
    2. Find suitable activities based on weather and preferences
    3. Suggest restaurants near the planned activities
    4. Plan transportation between locations
    5. Look for local events happening during the timeframe
    6. Track the overall budget and suggest cost optimizations

    Not all agents need to be called for every request. Use your judgment to \
    determine which agents are relevant based on what the user is asking for.

    Provide a comprehensive, well-organized final response that combines all \
    gathered information into a coherent activity plan.
    """;
  }

  @Override
  public List<Delegation> delegations() {
    return List.of(
      Delegation.toAgent(WeatherAgent.class).withDescription(
        "Provides weather forecasts for locations and dates"
      ),
      Delegation.toAgent(ActivityAgent.class).withDescription(
        "Suggests activities like team building, sports, games, city trips"
      ),
      Delegation.toAgent(RestaurantAgent.class).withDescription(
        "Recommends restaurants based on location, cuisine, budget, dietary needs"
      ),
      Delegation.toAgent(TransportAgent.class).withDescription(
        "Plans transportation including public transit, walking, costs, travel times"
      ),
      Delegation.toAgent(EventAgent.class).withDescription(
        "Finds local events, exhibitions, concerts, festivals"
      ),
      Delegation.toAgent(BudgetAgent.class).withDescription(
        "Tracks costs and manages budget constraints across all categories"
      )
    );
  }

  @Override
  public Class<String> resultType() {
    return String.class;
  }
}
