/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.autonomous;

import akka.annotation.DoNotInherit;
import akka.javasdk.agent.task.TaskKey;
import java.util.List;
import java.util.Optional;

/**
 * Summary of an autonomous agent's current state.
 *
 * <p>Not for user extension or instantiation, returned by the SDK component client.
 */
@DoNotInherit
public final class AgentState {

  private final String phase;
  private final boolean suspended;
  private final String goal;
  private final AutonomousAgent.TokenUsage totalTokenUsage;
  private final Optional<TaskKey> currentTask;
  private final List<String> pendingTaskIds;

  public AgentState(
      String phase,
      boolean suspended,
      String goal,
      AutonomousAgent.TokenUsage totalTokenUsage,
      Optional<TaskKey> currentTask,
      List<String> pendingTaskIds) {
    this.phase = phase;
    this.suspended = suspended;
    this.goal = goal;
    this.totalTokenUsage = totalTokenUsage;
    this.currentTask = currentTask;
    this.pendingTaskIds = pendingTaskIds;
  }

  /** The current phase of the agent (e.g. "idle", "running", "stopped"). */
  public String phase() {
    return phase;
  }

  /** Whether the agent is currently suspended. */
  public boolean suspended() {
    return suspended;
  }

  /** The agent's current goal. */
  public String goal() {
    return goal;
  }

  /** Total token usage for this agent instance. */
  public AutonomousAgent.TokenUsage totalTokenUsage() {
    return totalTokenUsage;
  }

  /** The task currently being worked on, if any. */
  public Optional<TaskKey> currentTask() {
    return currentTask;
  }

  /** The ids of tasks that are pending (queued but not yet started). */
  public List<String> pendingTaskIds() {
    return pendingTaskIds;
  }
}
