/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.autonomous;

import akka.javasdk.agent.autonomous.capability.AgentCapability;
import akka.javasdk.impl.agent.autonomous.AgentSetupImpl;

/**
 * Per-instance configuration applied on top of an agent's static {@link AgentDefinition}. Use this
 * to dynamically configure an agent instance at runtime, the instructions override the static
 * instructions, and capabilities extend the static capabilities.
 *
 * <p>The agent's {@link akka.javasdk.annotations.Component#description() @Component description} is
 * bound to the component class and cannot be overridden per instance.
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
   * Override the agent's instructions for this instance. If not set, the static instructions from
   * {@link AgentDefinition} are used.
   */
  AgentSetup instructions(String instructions);

  /** Add a capability for this instance, extending the static capabilities. */
  AgentSetup capability(AgentCapability capability);
}
