package com.example.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.StepName;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.swarm.Handoff;
import akka.javasdk.swarm.Swarm;
import akka.javasdk.swarm.SwarmOrchestratorAgent;
import akka.javasdk.swarm.SwarmResult;
import akka.javasdk.swarm.SwarmState;
import akka.javasdk.workflow.Workflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static java.time.Duration.ofSeconds;

/**
 * Activity planner swarm that orchestrates multiple specialist agents
 * to plan activities, check weather, find restaurants, plan transport,
 * discover events, and track budget.
 */
@Component(id = "activity-planner-swarm")
public class ActivityPlannerSwarm extends Workflow<SwarmState> {

  private static final Logger logger = LoggerFactory.getLogger(ActivityPlannerSwarm.class);

  private final ComponentClient componentClient;

  public ActivityPlannerSwarm(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  protected String instructions() {
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

  protected List<Handoff> handoffs() {
    return List.of(
        Handoff.toAgent(WeatherAgent.class)
            .withDescription("Provides weather forecasts for locations and dates"),
        Handoff.toAgent(ActivityAgent.class)
            .withDescription("Suggests activities like team building, sports, games, city trips"),
        Handoff.toAgent(RestaurantAgent.class)
            .withDescription("Recommends restaurants based on location, cuisine, budget, dietary needs"),
        Handoff.toAgent(TransportAgent.class)
            .withDescription("Plans transportation including public transit, walking, costs, travel times"),
        Handoff.toAgent(EventAgent.class)
            .withDescription("Finds local events, exhibitions, concerts, festivals"),
        Handoff.toAgent(BudgetAgent.class)
            .withDescription("Tracks costs and manages budget constraints across all categories")
    );
  }

  protected Class<String> resultType() {
    return String.class;
  }

  // Must extend Workflow directly, can't use Swarm base class, so the following is sdk implementation details

  /** Maximum number of LLM round-trips before forced termination. Default: 10. */
  protected int maxTurns() {
    return 10;
  }

  /** Tools available to the orchestrator LLM (in addition to handoff tools). Default: none. */
  protected List<Object> tools() {
    return List.of();
  }

  /** Step timeout for the orchestration step in seconds. */
  protected int stepTimeoutSeconds() {
    return 5*60;
  }

  /** Maximum retries for the orchestration step. Default: 1. */
  protected int maxRetries() {
    return 1;
  }

  // ========== Input accessor ==========

  /**
   * Access the input that was passed to {@code run}.
   * Available for use in {@link #instructions()}, {@link #handoffs()},
   * and {@link #tools()} to dynamically configure the swarm based on input.
   */
  protected String getInput() {
    return null; // provided by runtime
  }

  // ========== Workflow settings ==========

  @Override
  public WorkflowSettings settings() {
    return WorkflowSettings.builder()
        .defaultStepTimeout(ofSeconds(stepTimeoutSeconds()))
        .defaultStepRecovery(maxRetries(maxRetries()).failoverTo("error"))
        .build();
  }

  // ========== Built-in command handlers ==========

  /** Start the swarm with the given input. */
  public Effect<String> run(String input) {
    if (currentState() != null) {
      return effects().error("Swarm '" + commandContext().workflowId() + "' already started");
    }
    return effects()
        .updateState(SwarmState.initial(input, maxTurns()))
        .transitionTo("orchestrate")
        .thenReply("started");
  }

  /** Get the current result/status as a fully-typed SwarmResult. */
  public ReadOnlyEffect<SwarmResult<String>> getResult() {
    if (currentState() == null) {
      return effects().error("Swarm not started");
    }
    return effects().reply(currentState().toSwarmResult());
  }

  // ========== Workflow steps ==========

  @StepName("orchestrate")
  private StepEffect orchestrateStep() {
    var state = currentState();
    logger.info("Starting orchestration for: {}", state.userMessage());

    var request = new SwarmOrchestratorAgent.OrchestrateRequest(
        instructions(),
        handoffs(),
        state.userMessage(),
        sessionId());

    var result = componentClient
        .forAgent()
        .inSession(sessionId())
        .method(SwarmOrchestratorAgent::orchestrate)
        .invoke(request);

    logger.info("""
            Swarm completed, final result:
            --------------------------------------------------------------------------------------------
            {}
            --------------------------------------------------------------------------------------------""",
        result);

    return stepEffects()
        .updateState(state.completed(result))
        .thenEnd();
  }

  @StepName("error")
  private StepEffect errorStep() {
    logger.error("Orchestration failed for: {}", currentState().userMessage());
    return stepEffects()
        .updateState(currentState().failed("Orchestration failed after retries"))
        .thenEnd();
  }

  private String sessionId() {
    return commandContext().workflowId();
  }
}
