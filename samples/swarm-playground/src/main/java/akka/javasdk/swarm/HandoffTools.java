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
import java.util.Set;
import java.util.stream.Collectors;

/**
 * External tool class that bridges LLM handoff calls to real agent invocations.
 * The orchestrator LLM calls the handoff tool to delegate work to specialist agents.
 */
public class HandoffTools {

  private static final Logger logger = LoggerFactory.getLogger(HandoffTools.class);

  private final ComponentClient componentClient;
  private final String sessionId;
  private final List<Handoff> handoffs;
  private final Set<String> allowedAgentIds;

  public HandoffTools(ComponentClient componentClient, String sessionId, List<Handoff> handoffs) {
    this.componentClient = componentClient;
    this.sessionId = sessionId;
    this.handoffs = handoffs;
    this.allowedAgentIds = handoffs.stream()
        .map(h -> switch (h) {
          case Handoff.AgentHandoff a -> a.componentId();
          case Handoff.SwarmHandoff s -> s.componentId();
        })
        .collect(Collectors.toSet());
  }

  @FunctionTool(description = """
      Delegate a task to a specialist agent. Use this to hand off work to agents \
      that have specific expertise. The agent will process the request and return \
      its response. You can call multiple agents sequentially to gather information \
      from different specialists.""")
  public String handoff(
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

  /**
   * Generates a text description of available agents for inclusion in the system prompt.
   */
  public String describeHandoffs() {
    var sb = new StringBuilder("Available specialist agents:\n");
    for (var handoff : handoffs) {
      switch (handoff) {
        case Handoff.AgentHandoff a -> {
          sb.append("- ").append(a.componentId());
          a.description().ifPresent(d -> sb.append(": ").append(d));
          sb.append("\n");
        }
        case Handoff.SwarmHandoff s -> {
          sb.append("- ").append(s.componentId()).append(" (sub-swarm)");
          s.description().ifPresent(d -> sb.append(": ").append(d));
          sb.append("\n");
        }
      }
    }
    return sb.toString();
  }

  private static String truncate(String text) {
    return text.length() <= 200 ? text : text.substring(0, 200) + "...";
  }
}
