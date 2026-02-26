/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.task;

import akka.Done;
import akka.javasdk.client.ComponentStreamMethodRef;
import java.util.List;

/**
 * Interface for interacting with tasks in the autonomous agent system.
 *
 * <p>Tasks represent units of work that autonomous agents process. Each task has a lifecycle:
 * PENDING -> IN_PROGRESS -> COMPLETED or FAILED.
 *
 * <p>The result type {@code R} defines the expected structure of the task's output. When an
 * autonomous agent completes the task, the LLM is guided to produce results conforming to the JSON
 * schema of {@code R}. When reading the result, the stored JSON is deserialized into {@code R}.
 *
 * <p>The default implementation is backed by {@link TaskEntity}, a built-in Event Sourced Entity.
 *
 * @param <R> The type of the task result.
 */
public interface Task<R> {

  /** Create a new task with the given description. */
  default Done create(String description) {
    return create(description, List.of());
  }

  /**
   * Create a new task with the given description and dependencies. The task will not be worked on
   * until all dependency tasks have completed.
   *
   * @param description the task description
   * @param dependencyTaskIds IDs of tasks that must complete before this task can start
   */
  Done create(String description, List<String> dependencyTaskIds);

  /** Get the current state of the task, with a typed result. */
  TaskState getState();

  /** Get the current state with the result deserialized to type {@code R}. */
  R getResult();

  /**
   * Subscribe to real-time notifications for this task's lifecycle events. Notifications follow the
   * task — they are published regardless of which agent is processing it, surviving handoffs.
   *
   * <p>Example usage:
   *
   * <pre>{@code
   * componentClient.forTask(taskId, MyResult.class)
   *     .notifications()
   *     .source()
   *     .runForeach(notification -> System.out.println("Event: " + notification), materializer);
   * }</pre>
   */
  ComponentStreamMethodRef<TaskNotification> notifications();

  /**
   * Provide input for a pending decision point. This resumes the agent's processing after a
   * decision was requested via the {@code requestDecision} tool.
   *
   * @param decisionId the ID of the pending decision (from the {@link
   *     TaskNotification.DecisionRequested} notification)
   * @param response the response to provide to the agent
   */
  Done provideInput(String decisionId, String response);
}
