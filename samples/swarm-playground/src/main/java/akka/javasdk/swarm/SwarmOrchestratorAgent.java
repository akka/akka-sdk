/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.swarm;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Internal orchestrator agent used by the Swarm framework.
 * Coordinates specialist agents and sub-swarms via handoff tool calls
 * within a single LLM tool-calling loop.
 */
@Component(id = "swarm-orchestrator")
public class SwarmOrchestratorAgent extends Agent {

  private static final Logger logger = LoggerFactory.getLogger(SwarmOrchestratorAgent.class);
  private static final long SWARM_POLL_INTERVAL_MS = 3000;
  private static final long SWARM_POLL_TIMEOUT_MS = 10 * 60 * 1000; // 10 minutes

  public record OrchestrateRequest(
      String instructions,
      List<Handoff> handoffs,
      String userMessage,
      String sessionId) {}

  private static final String META_INSTRUCTIONS = """

      ## How to use handoffs
      You have access to handoff tools that let you delegate tasks to specialist agents \
      and sub-swarms. Each has a specific area of expertise.

      For agents, use the 'handoff' tool. For sub-swarms, use the 'swarmHandoff' tool.

      Strategy:
      1. Analyze the user's request and identify which specialists are needed
      2. Call each relevant agent or swarm using the appropriate handoff tool
      3. Collect and synthesize the responses
      4. Provide a comprehensive final answer that combines all the gathered information

      Important:
      - Call agents one at a time and wait for each response before deciding next steps
      - Sub-swarms may take longer as they coordinate multiple agents internally
      - If an agent or swarm returns an error, try rephrasing or skip that aspect
      - Always provide a final synthesized answer after gathering all needed information
      """;

  private final ComponentClient componentClient;

  public SwarmOrchestratorAgent(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<String> orchestrate(OrchestrateRequest request) {
    var swarmInvokers = buildSwarmInvokers(request.handoffs());
    var handoffTools = new HandoffTools(
        componentClient, request.sessionId(), request.handoffs(), swarmInvokers);

    var systemMessage = request.instructions() + "\n\n"
        + handoffTools.describeHandoffs() + "\n"
        + META_INSTRUCTIONS;

    return effects()
        .systemMessage(systemMessage)
        .tools(handoffTools)
        .userMessage(request.userMessage())
        .thenReply();
  }

  /**
   * Builds SwarmInvoker instances for each SwarmHandoff.
   * Each invoker starts the swarm workflow and polls until completion.
   *
   * Prototype limitation: uses a switch on known swarm component IDs since
   * there's no dynamicCall for workflows.
   */
  private Map<String, HandoffTools.SwarmInvoker> buildSwarmInvokers(List<Handoff> handoffs) {
    var invokers = new HashMap<String, HandoffTools.SwarmInvoker>();
    for (var handoff : handoffs) {
      if (handoff instanceof Handoff.SwarmHandoff swarmHandoff) {
        var swarmId = swarmHandoff.componentId();
        invokers.put(swarmId, (workflowId, input) -> startAndPollSwarm(swarmId, workflowId, input));
      }
    }
    return invokers;
  }

  /**
   * Starts a swarm workflow and polls for its result.
   * Uses a switch on known swarm IDs to call the concrete workflow class.
   */
  private String startAndPollSwarm(String swarmId, String workflowId, String input) {
    // Start the swarm
    startSwarm(swarmId, workflowId, input);

    // Poll until completed or failed
    long deadline = System.currentTimeMillis() + SWARM_POLL_TIMEOUT_MS;
    while (System.currentTimeMillis() < deadline) {
      try {
        Thread.sleep(SWARM_POLL_INTERVAL_MS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return "ERROR: Swarm polling interrupted";
      }

      SwarmResult<String> result = getSwarmResult(swarmId, workflowId);
      switch (result) {
        case SwarmResult.Completed<String> c -> {
          return c.result();
        }
        case SwarmResult.Failed<String> f -> {
          return "ERROR: Swarm failed: " + f.reason();
        }
        case SwarmResult.Running<String> r -> {
          logger.debug("Swarm '{}' still running (turn {}/{})", swarmId, r.currentTurn(), r.maxTurns());
        }
        default -> {
          logger.debug("Swarm '{}' in state: {}", swarmId, result);
        }
      }
    }
    return "ERROR: Swarm '" + swarmId + "' timed out after " + (SWARM_POLL_TIMEOUT_MS / 1000) + "s";
  }

  /**
   * Start a swarm workflow by component ID.
   * Prototype: switch on known swarm IDs.
   * Add new swarm classes here as they are created.
   */
  private void startSwarm(String swarmId, String workflowId, String input) {
    switch (swarmId) {
      case "activity-planner-swarm" ->
          componentClient.forWorkflow(workflowId)
              .method(com.example.application.ActivityPlannerSwarm::run)
              .invoke(input);
      case "content-refinement-swarm" ->
          componentClient.forWorkflow(workflowId)
              .method(com.example.application.ContentRefinementSwarm::run)
              .invoke(input);
      case "content-creation-swarm" ->
          componentClient.forWorkflow(workflowId)
              .method(com.example.application.ContentCreationSwarm::run)
              .invoke(input);
      case "triage-swarm" ->
          componentClient.forWorkflow(workflowId)
              .method(com.example.application.TriageSwarm::run)
              .invoke(input);
      default -> throw new IllegalArgumentException("Unknown swarm: " + swarmId);
    }
  }

  /**
   * Get the result of a swarm workflow by component ID.
   * Prototype: switch on known swarm IDs.
   */
  @SuppressWarnings("unchecked")
  private SwarmResult<String> getSwarmResult(String swarmId, String workflowId) {
    return switch (swarmId) {
      case "activity-planner-swarm" ->
          componentClient.forWorkflow(workflowId)
              .method(com.example.application.ActivityPlannerSwarm::getResult)
              .invoke();
      case "content-refinement-swarm" ->
          componentClient.forWorkflow(workflowId)
              .method(com.example.application.ContentRefinementSwarm::getResult)
              .invoke();
      case "content-creation-swarm" ->
          componentClient.forWorkflow(workflowId)
              .method(com.example.application.ContentCreationSwarm::getResult)
              .invoke();
      case "triage-swarm" ->
          componentClient.forWorkflow(workflowId)
              .method(com.example.application.TriageSwarm::getResult)
              .invoke();
      default -> throw new IllegalArgumentException("Unknown swarm: " + swarmId);
    };
  }
}
