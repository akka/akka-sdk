/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.autonomous;

import java.util.List;

/** Internal representation of agent capabilities. */
sealed interface AgentCapability permits AgentCapability.Delegation, AgentCapability.Handoff {

  record Delegation(List<Class<? extends AutonomousAgent>> agents) implements AgentCapability {}

  record Handoff(List<Class<? extends AutonomousAgent>> agents) implements AgentCapability {}
}
