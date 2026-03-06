/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.autonomous;

/**
 * Strategy for an autonomous agent. Determines how the agent executes. An agent declares its
 * strategy by overriding {@link AutonomousAgent#strategy()}.
 *
 * @see AutonomousStrategy
 */
public sealed interface Strategy permits AutonomousStrategy {

  /** Create an autonomous (LLM-based) strategy. */
  static AutonomousStrategy autonomous() {
    return new DefaultAutonomousStrategy();
  }
}
