/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import java.util.Set;

/**
 * The registry contains information about all agents. This is useful when implementing planners for
 * multi-agent collaboration.
 *
 * <p>It can be injected as a constructor parameter in components.
 *
 * <p>The agent id, name, and description are defined with the {@link
 * akka.javasdk.annotations.Component} annotation on the agent class. The agent role is defined with
 * the {@link akka.javasdk.annotations.AgentRole} annotation on the agent class.
 *
 * <p>Note that agents can be called based on the id without knowing the exact agent class or lambda
 * of the agent method by using the {{@code dynamicCall}} of the component client.
 *
 * <pre>{@code
 * var response =
 *   componentClient
 *     .forAgent().inSession(sessionId)
 *     .dynamicCall(agentId)
 *     .invoke(request);
 * }</pre>
 *
 * <p>Not for user extension, implementation provided by the SDK.
 */
public interface AgentRegistry {
  record AgentInfo(String id, String name, String description, String role) {
    public boolean hasRole(String r) {
      return role != null && role.equals(r);
    }
  }

  /**
   * Information about all agents. The agent id, name, and description are defined with the {@link
   * akka.javasdk.annotations.Component} annotation on the agent class. The agent role is defined
   * with the {@link akka.javasdk.annotations.AgentRole} annotation on the agent class.
   */
  Set<AgentInfo> allAgents();

  /**
   * Agent identifiers of agents with a certain role. The role is defined with {@link
   * akka.javasdk.annotations.AgentRole} annotation on the agent class. The agent id, name, and
   * description are defined with the {@link akka.javasdk.annotations.Component} annotation on the
   * agent class.
   */
  Set<AgentInfo> agentsWithRole(String role);

  /**
   * Information about a given agent. The agent id, name, and description are defined with the
   * {@link akka.javasdk.annotations.Component} annotation on the agent class. The agent role is
   * defined with the {@link akka.javasdk.annotations.AgentRole} annotation on the agent class.
   */
  AgentInfo agentInfo(String agentId);
}
