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
  private final String instructions;
  private final AutonomousAgent.TokenUsage totalTokenUsage;
  private final Optional<TaskKey> currentTask;
  private final List<String> pendingTaskIds;
  private final List<MessageState> pendingMessages;
  private final List<MessageState> recentMessages;

  public AgentState(
      String phase,
      boolean suspended,
      String instructions,
      AutonomousAgent.TokenUsage totalTokenUsage,
      Optional<TaskKey> currentTask,
      List<String> pendingTaskIds,
      List<MessageState> pendingMessages,
      List<MessageState> recentMessages) {
    this.phase = phase;
    this.suspended = suspended;
    this.instructions = instructions;
    this.totalTokenUsage = totalTokenUsage;
    this.currentTask = currentTask;
    this.pendingTaskIds = pendingTaskIds;
    this.pendingMessages = pendingMessages;
    this.recentMessages = recentMessages;
  }

  /**
   * @deprecated Use {@link #AgentState(String, boolean, String, AutonomousAgent.TokenUsage,
   *     Optional, List, List, List)}.
   */
  @Deprecated(since = "3.7.2", forRemoval = true)
  public AgentState(
      String phase,
      boolean suspended,
      String instructions,
      AutonomousAgent.TokenUsage totalTokenUsage,
      Optional<TaskKey> currentTask,
      List<String> pendingTaskIds) {
    this(
        phase,
        suspended,
        instructions,
        totalTokenUsage,
        currentTask,
        pendingTaskIds,
        List.of(),
        List.of());
  }

  /**
   * The current phase of the agent's execution loop: "idle", "advance" (determining the next unit
   * of work), "model" (a model call is in progress), "tools" (tool calls are executing), or
   * "stopped".
   */
  public String phase() {
    return phase;
  }

  /** Whether the agent is currently suspended. */
  public boolean suspended() {
    return suspended;
  }

  /** The agent's current instructions. */
  public String instructions() {
    return instructions;
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

  /**
   * The messages that clients sent with {@link
   * akka.javasdk.client.AutonomousAgentClient#sendMessage(AgentMessage)} and that wait for the
   * model, in arrival order. The status is {@link MessageStatus.ForCurrentTask} or {@link
   * MessageStatus.Held}.
   */
  public List<MessageState> pendingMessages() {
    return pendingMessages;
  }

  /**
   * The most recent messages that clients sent with {@link
   * akka.javasdk.client.AutonomousAgentClient#sendMessage(AgentMessage)} and that the agent
   * consumed or dropped, oldest first. The status is {@link MessageStatus.Consumed} or {@link
   * MessageStatus.Dropped}. A retry with one of these ids gets this status.
   */
  public List<MessageState> recentMessages() {
    return recentMessages;
  }
}
