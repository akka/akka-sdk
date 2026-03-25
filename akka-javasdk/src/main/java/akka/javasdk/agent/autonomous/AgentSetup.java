/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.autonomous;

import akka.javasdk.agent.autonomous.capability.AgentCapability;
import akka.javasdk.impl.agent.autonomous.AgentSetupImpl;

/**
 * Per-instance configuration applied on top of an agent's static {@link AgentDefinition}. Use this
 * to dynamically configure an agent instance at runtime — the goal overrides the static goal, and
 * capabilities extend the static capabilities.
 *
 * <p>Each fluent method returns a new immutable instance.
 *
 * @see AutonomousAgent#definition()
 */
public interface AgentSetup {

  /** Create an empty agent setup. */
  static AgentSetup create() {
    return AgentSetupImpl.empty();
  }

  /**
   * Override the agent's goal for this instance. If not set, the static goal from {@link
   * AgentDefinition} is used.
   */
  AgentSetup goal(String goal);

  /** Add a capability for this instance, extending the static capabilities. */
  AgentSetup capability(AgentCapability capability);
}
