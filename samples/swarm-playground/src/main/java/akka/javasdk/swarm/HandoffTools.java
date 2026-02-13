/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.swarm;

import akka.javasdk.annotations.Description;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.client.ComponentClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * External tool class that bridges LLM handoff calls to real agent and swarm invocations.
 * The orchestrator LLM calls the handoff tool to delegate work to specialist agents,
 * and the swarmHandoff tool to delegate to sub-swarms.
 */
public class HandoffTools {

  private static final Logger logger = LoggerFactory.getLogger(HandoffTools.class);
  private final ComponentClient componentClient;
  private final String sessionId;
  private final List<Handoff> handoffs;
  private final Set<String> allowedAgentIds;
  private final Set<String> allowedSwarmIds;
  private final Map<String, SwarmInvoker> swarmInvokers;

  /**
   * Functional interface for invoking a swarm by its component ID.
   * The implementation starts the swarm and polls for the result.
   */
  @FunctionalInterface
  public interface SwarmInvoker {
    /**
     * Run the swarm with the given input and workflow ID, and return the result.
     */
    String invoke(String workflowId, String input);
  }

  public HandoffTools(ComponentClient componentClient, String sessionId, List<Handoff> handoffs) {
    this(componentClient, sessionId, handoffs, Map.of());
  }

  public HandoffTools(ComponentClient componentClient, String sessionId,
                      List<Handoff> handoffs, Map<String, SwarmInvoker> swarmInvokers) {
    this.componentClient = componentClient;
    this.sessionId = sessionId;
    this.handoffs = handoffs;
    this.swarmInvokers = swarmInvokers;
    this.allowedAgentIds = handoffs.stream()
        .filter(h -> h instanceof Handoff.AgentHandoff)
        .map(h -> ((Handoff.AgentHandoff) h).componentId())
        .collect(Collectors.toSet());
    this.allowedSwarmIds = handoffs.stream()
        .filter(h -> h instanceof Handoff.SwarmHandoff)
        .map(h -> ((Handoff.SwarmHandoff) h).componentId())
        .collect(Collectors.toSet());
  }

  @FunctionTool(description = """
      Delegate a task to a specialist agent. Use this to hand off work to agents \
      that have specific expertise. The agent will process the request and return \
      its response. You can call multiple agents sequentially to gather information \
      from different specialists.""")
  public String agentHandoff(
      @Description("The agent ID to delegate to, e.g. 'weather-agent'") String agentId,
      @Description("The request/question to send to the agent") String request) {

    if (!allowedAgentIds.contains(agentId)) {
      return "ERROR: Unknown agent '" + agentId + "'. Available agents: " + allowedAgentIds;
    }

    logger.info("Handoff to agent '{}': {}", agentId, truncate(request));

    try {
      String response = componentClient
          .forAgent()
          .inSession(sessionId)
          .<String, String>dynamicCall(agentId)
          .invoke(request);

      logger.info("Handoff response from agent '{}': {}", agentId, truncate(response));
      return response;
    } catch (Exception e) {
      logger.error("Handoff to '{}' failed", agentId, e);
      return "ERROR: Failed to reach agent '" + agentId + "': " + e.getMessage();
    }
  }

  @FunctionTool(description = """
      Delegate a complex task to a sub-swarm. A swarm is a coordinated group of agents \
      that work together on a larger task. The swarm will run autonomously and return \
      the final result. Use this for tasks that require multi-agent coordination.""")
  public String swarmHandoff(
      @Description("The swarm ID to delegate to, e.g. 'content-refinement-swarm'") String swarmId,
      @Description("The task description/brief to send to the swarm") String request) {

    if (!allowedSwarmIds.contains(swarmId)) {
      return "ERROR: Unknown swarm '" + swarmId + "'. Available swarms: " + allowedSwarmIds;
    }

    var invoker = swarmInvokers.get(swarmId);
    if (invoker == null) {
      return "ERROR: No invoker registered for swarm '" + swarmId + "'";
    }

    var workflowId = UUID.randomUUID().toString();
    logger.info("Swarm handoff to '{}' (workflow {}): {}", swarmId, workflowId, truncate(request));

    try {
      String result = invoker.invoke(workflowId, request);
      logger.info("Swarm handoff response from '{}': {}", swarmId, truncate(result));
      return result;
    } catch (Exception e) {
      logger.error("Swarm handoff to '{}' failed", swarmId, e);
      return "ERROR: Swarm '" + swarmId + "' failed: " + e.getMessage();
    }
  }

  /**
   * Generates a text description of available handoff targets for inclusion in the system prompt.
   */
  public String describeHandoffs() {
    var sb = new StringBuilder();
    var agents = handoffs.stream().filter(h -> h instanceof Handoff.AgentHandoff).toList();
    var swarms = handoffs.stream().filter(h -> h instanceof Handoff.SwarmHandoff).toList();

    if (!agents.isEmpty()) {
      sb.append("Available specialist agents (use the 'handoff' tool):\n");
      for (var handoff : agents) {
        var a = (Handoff.AgentHandoff) handoff;
        sb.append("- ").append(a.componentId());
        a.description().ifPresent(d -> sb.append(": ").append(d));
        sb.append("\n");
      }
    }
    if (!swarms.isEmpty()) {
      sb.append("\nAvailable sub-swarms (use the 'swarmHandoff' tool):\n");
      for (var handoff : swarms) {
        var s = (Handoff.SwarmHandoff) handoff;
        sb.append("- ").append(s.componentId());
        s.description().ifPresent(d -> sb.append(": ").append(d));
        sb.append("\n");
      }
    }
    return sb.toString();
  }

  private static String truncate(String text) {
    return text.length() <= 200 ? text : text.substring(0, 200) + "...";
  }
}
