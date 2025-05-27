/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import java.util.Set;

public interface AgentRegistry {

  Set<String> allAgentIds();

  Set<String> agentIdsWithRole(String role);

  String agentDescriptionAsJson(String agentId);

}
