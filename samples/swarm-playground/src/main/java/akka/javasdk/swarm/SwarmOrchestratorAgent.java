/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.swarm;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;

import java.util.List;

/**
 * Internal orchestrator agent used by the Swarm framework.
 * Coordinates specialist agents via handoff tool calls within a single
 * LLM tool-calling loop.
 */
@Component(id = "swarm-orchestrator")
public class SwarmOrchestratorAgent extends Agent {

  public record OrchestrateRequest(
      String instructions,
      List<Handoff> handoffs,
      String userMessage,
      String sessionId) {}

  private static final String META_INSTRUCTIONS = """

      ## How to use handoffs
      You have access to a `handoff` tool that lets you delegate tasks to specialist agents.
      Each agent has a specific area of expertise. Use the handoff tool to call the right \
      agent for each part of the task.

      Strategy:
      1. Analyze the user's request and identify which specialists are needed
      2. Call each relevant agent using the handoff tool with a clear, specific request
      3. Collect and synthesize the responses from all agents
      4. Provide a comprehensive final answer that combines all the gathered information

      Important:
      - Call agents one at a time and wait for each response before deciding next steps
      - If an agent returns an error, try rephrasing or skip that aspect
      - Always provide a final synthesized answer after gathering all needed information
      """;

  private final ComponentClient componentClient;

  public SwarmOrchestratorAgent(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<String> orchestrate(OrchestrateRequest request) {
    var handoffTools = new HandoffTools(
        componentClient, request.sessionId(), request.handoffs());

    var systemMessage = request.instructions() + "\n\n"
        + handoffTools.describeHandoffs() + "\n"
        + META_INSTRUCTIONS;

    return effects()
        .systemMessage(systemMessage)
        .tools(handoffTools)
        .userMessage(request.userMessage())
        .thenReply();
  }
}
