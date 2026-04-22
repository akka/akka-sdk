/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.AgentRegistry;
import akka.javasdk.annotations.AgentRole;
import akka.javasdk.annotations.Component;
import org.junit.jupiter.api.Test;

public class AgentDescriptionSupportTest {

  @Component(
      id = "modern-agent",
      name = "Modern Agent",
      description = "A modern agent using Component and AgentRole.")
  @AgentRole("modern-role")
  static class ModernAgent extends Agent {
    // ...existing code...
  }

  @Test
  public void testComponentAndAgentRoleSupport() throws Exception {
    AgentRegistry.AgentInfo info =
        AgentRegistryImpl.agentDetailsFor(ModernAgent.class).toAgentInfo();
    assertEquals("Modern Agent", info.name());
    assertEquals("A modern agent using Component and AgentRole.", info.description());
    assertEquals("modern-role", info.role());
  }
}
