/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import java.util.Set;

public interface AgentRegistry {
  record AgentInfo(String id, String name, String description, String role) {}

  Set<String> allAgentIds();

  Set<String> agentIdsWithRole(String role);

  AgentInfo agentInfo(String agentId);

  String agentInfoAsJson(String agentId);

}
