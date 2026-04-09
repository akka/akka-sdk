/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.autonomous;

import akka.annotation.DoNotInherit;

/**
 * Summary of an autonomous agent's current state.
 *
 * <p>Not for user extension or instantiation, returned by the SDK component client.
 */
@DoNotInherit
public final class AgentState {

  private final String phase;
  private final boolean paused;
  private final String goal;
  private final AutonomousAgent.TokenUsage totalTokenUsage;

  public AgentState(
      String phase, boolean paused, String goal, AutonomousAgent.TokenUsage totalTokenUsage) {
    this.phase = phase;
    this.paused = paused;
    this.goal = goal;
    this.totalTokenUsage = totalTokenUsage;
  }

  /** The current phase of the agent (e.g. "idle", "running", "stopped"). */
  public String phase() {
    return phase;
  }

  /** Whether the agent is currently paused. */
  public boolean paused() {
    return paused;
  }

  /** The agent's current goal. */
  public String goal() {
    return goal;
  }

  /** Total token usage for this agent instance. */
  public AutonomousAgent.TokenUsage totalTokenUsage() {
    return totalTokenUsage;
  }
}
