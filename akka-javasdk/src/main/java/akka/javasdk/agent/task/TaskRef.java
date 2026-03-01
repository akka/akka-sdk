/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.task;

/**
 * A typed handle to a running or completed task instance. Carries the task ID and result type,
 * providing type-safe retrieval without repeating the result type at every call site.
 *
 * <p>Returned by {@link akka.javasdk.client.AutonomousAgentClient#runSingleTask} and created via
 * {@link TaskDef#ref(String)}.
 *
 * @param <R> The result type of the task.
 */
public final class TaskRef<R> {

  private final String taskId;
  private final String description;
  private final Class<R> resultType;

  TaskRef(String taskId, String description, Class<R> resultType) {
    this.taskId = taskId;
    this.description = description;
    this.resultType = resultType;
  }

  /** The unique ID of this task instance. */
  public String taskId() {
    return taskId;
  }

  /** The description from the task definition. */
  public String description() {
    return description;
  }

  /** The expected result type. */
  public Class<R> resultType() {
    return resultType;
  }
}
