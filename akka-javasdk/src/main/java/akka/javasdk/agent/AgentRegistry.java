/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import java.util.Set;

interface AgentRegistry {
  interface AgentRef<A, B> {
    B invoke(A arg);
  }

  Set<String> allAgentIds();

  Set<String> agentIdsWithRole(String role);

  String agentDescriptionAsJson(String agentId);

  <A, B> AgentRef<A, B> getAgent(String agentId);
}
