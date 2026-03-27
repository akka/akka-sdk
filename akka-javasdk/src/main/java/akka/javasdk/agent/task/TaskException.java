/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.task;

/**
 * Base exception for task terminal states. Thrown by {@link
 * akka.javasdk.client.TaskClient#resultAsync} when a task reaches a non-successful terminal state.
 */
public abstract sealed class TaskException extends RuntimeException {

  private final String taskId;
  private final String reason;

  private TaskException(String taskId, String reason) {
    super(reason);
    this.taskId = taskId;
    this.reason = reason;
  }

  /** The ID of the task that failed or was cancelled. */
  public String taskId() {
    return taskId;
  }

  /** The reason the task failed or was cancelled. */
  public String reason() {
    return reason;
  }

  /** Thrown when a task reaches the {@link TaskStatus#FAILED} state. */
  public static final class Failed extends TaskException {
    public Failed(String taskId, String reason) {
      super(taskId, reason);
    }
  }

  /** Thrown when a task reaches the {@link TaskStatus#CANCELLED} state. */
  public static final class Cancelled extends TaskException {
    public Cancelled(String taskId, String reason) {
      super(taskId, reason);
    }
  }
}
