/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import java.util.Set;

/**
 * The registry contains information about all agents. This is useful
 * when implementing planners for multi-agent collaboration.
 * <p>
 * It can be injected as a constructor parameter in components.
 * <p>
 * The agent id is defined with the {@link akka.javasdk.annotations.ComponentId}
 * annotation on the agent class. Additional information is defined with the
 * {@link akka.javasdk.annotations.AgentDescription} annotation on the agent class.
 * <p>
 * Note that agents can be called based on the id without knowing the exact agent class
 * or lambda of the agent method by using the {{@code dynamicCall}} of the component
 * client.
 *
 * <pre>{@code
 * var response =
 *   componentClient
 *     .forAgent().inSession(sessionId)
 *     .dynamicCall(agentId)
 *     .invoke(request);
 * }</pre>
 * <p>
 * Not for user extension, implementation provided by the SDK.
 */
public interface AgentRegistry {
  record AgentInfo(String id, String name, String description, String role) {}

  /**
   * All agent identifiers. The agent id is defined with the {@link akka.javasdk.annotations.ComponentId}
   * annotation on the agent class.
   */
  Set<String> allAgentIds();

  /**
   * Agent identifiers of agents with a certain role. The role is defined with
   * {@link akka.javasdk.annotations.AgentDescription} annotation on the agent class.
   * The agent id is defined with the {@link akka.javasdk.annotations.ComponentId}
   * annotation on the agent class.
   */
  Set<String> agentIdsWithRole(String role);

  /**
   * Information about a given agent.
   */
  AgentInfo agentInfo(String agentId);

  /**
   * Information about a given agent in JSON format.
   */
  String agentInfoAsJson(String agentId);
}
