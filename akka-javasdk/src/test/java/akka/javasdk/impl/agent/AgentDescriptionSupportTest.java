/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.AgentRegistry;
import akka.javasdk.annotations.AgentDescription;
import akka.javasdk.annotations.AgentRole;
import akka.javasdk.annotations.Component;
import org.junit.jupiter.api.Test;

public class AgentDescriptionSupportTest {

  @SuppressWarnings("deprecation")
  @AgentDescription(
      name = "Legacy Agent",
      description = "A legacy agent using the deprecated annotation.",
      role = "legacy-role")
  static class LegacyAgent extends Agent {
    // ...existing code...
  }

  @Component(
      id = "modern-agent",
      name = "Modern Agent",
      description = "A modern agent using Component and AgentRole.")
  @AgentRole("modern-role")
  static class ModernAgent extends Agent {
    // ...existing code...
  }

  @Test
  public void testAgentDescriptionSupport() throws Exception {
    AgentRegistry.AgentInfo info =
        AgentRegistryImpl.agentDetailsFor(LegacyAgent.class).toAgentInfo();
    assertEquals("Legacy Agent", info.name());
    assertEquals("A legacy agent using the deprecated annotation.", info.description());
    assertEquals("legacy-role", info.role());
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
