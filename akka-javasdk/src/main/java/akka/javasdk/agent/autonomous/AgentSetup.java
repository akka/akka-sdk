/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.autonomous;

import akka.javasdk.agent.autonomous.capability.AgentCapability;
import akka.javasdk.impl.agent.autonomous.AgentSetupImpl;

/**
 * Per-instance configuration applied on top of an agent's static {@link AgentDefinition}. Use this
 * to dynamically configure an agent instance at runtime — purpose and guidance override their
 * static counterparts, and capabilities extend the static capabilities.
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
   * Override the agent's purpose for this instance. If not set, the static purpose from {@link
   * AgentDefinition} is used.
   */
  AgentSetup purpose(String purpose);

  /**
   * Override the agent's guidance for this instance. If not set, the static guidance from {@link
   * AgentDefinition} is used.
   */
  AgentSetup guidance(String guidance);

  /** Add a capability for this instance, extending the static capabilities. */
  AgentSetup capability(AgentCapability capability);
}
