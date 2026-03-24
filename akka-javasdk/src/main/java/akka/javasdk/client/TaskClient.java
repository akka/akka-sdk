/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.client;

import akka.annotation.DoNotInherit;
import akka.javasdk.agent.task.Task;
import akka.javasdk.agent.task.TaskDefinition;
import akka.javasdk.agent.task.TaskSnapshot;
import java.util.concurrent.CompletionStage;

/**
 * Client for creating, querying, and completing tasks, bound to a specific task entity ID.
 *
 * <p>Not for user extension or instantiation, returned by the SDK component client.
 */
@DoNotInherit
public interface TaskClient {

  /**
   * Create a task entity. The task definition provides the type, description, and instructions.
   * Returns the task ID (the same ID this client was created with).
   *
   * @param task the task to create, with instructions and any attachments
   * @param <R> the result type of the task
   * @return the task ID
   */
  default <R> String create(Task<R> task) {
    return createAsync(task).toCompletableFuture().join();
  }

  /**
   * Async variant of {@link #create}.
   *
   * @param task the task to create, with instructions and any attachments
   * @param <R> the result type of the task
   * @return a CompletionStage with the task ID
   */
  <R> CompletionStage<String> createAsync(Task<R> task);

  /**
   * Assign a task to an owner. A task must be assigned before it can be completed or failed. For
   * agent-processed tasks, assignment is handled by the runtime. For tasks completed by humans or
   * other processes, the application assigns the task via this method.
   *
   * @param assignee identifier of the owner (e.g., a user ID, team name, or process identifier)
   */
  default void assign(String assignee) {
    assignAsync(assignee).toCompletableFuture().join();
  }

  /**
   * Async variant of {@link #assign}.
   *
   * @param assignee identifier of the owner (e.g., a user ID, team name, or process identifier)
   * @return a CompletionStage that completes when the task has been assigned
   */
  CompletionStage<Void> assignAsync(String assignee);

  /**
   * Complete a task with a result. The task must be assigned first (via {@link #assign} or by the
   * agent runtime). For tasks not yet in progress, completion is allowed directly from the assigned
   * state — no separate start step is needed.
   *
   * @param result the task result, serialized to JSON
   */
  default void complete(Object result) {
    completeAsync(result).toCompletableFuture().join();
  }

  /**
   * Async variant of {@link #complete}.
   *
   * @param result the task result, serialized to JSON
   * @return a CompletionStage that completes when the task has been completed
   */
  CompletionStage<Void> completeAsync(Object result);

  /**
   * Fail a task with a reason. The task must be assigned first (via {@link #assign} or by the agent
   * runtime).
   *
   * @param reason the failure reason
   */
  default void fail(String reason) {
    failAsync(reason).toCompletableFuture().join();
  }

  /**
   * Async variant of {@link #fail}.
   *
   * @param reason the failure reason
   * @return a CompletionStage that completes when the task has been failed
   */
  CompletionStage<Void> failAsync(String reason);

  // --- querying ---

  /**
   * Get a point-in-time snapshot of the task's current state.
   *
   * @param taskDefinition the task definition, used for typed result deserialization
   * @param <R> the result type of the task
   * @return the task snapshot containing status and typed result
   */
  default <R> TaskSnapshot<R> get(TaskDefinition<R> taskDefinition) {
    return getAsync(taskDefinition).toCompletableFuture().join();
  }

  /**
   * Async variant of {@link #get}.
   *
   * @param taskDefinition the task definition, used for typed result deserialization
   * @param <R> the result type of the task
   * @return a CompletionStage with the task snapshot containing status and typed result
   */
  <R> CompletionStage<TaskSnapshot<R>> getAsync(TaskDefinition<R> taskDefinition);

  /**
   * Blocks until the task reaches a terminal state and returns the typed result. Throws {@link
   * akka.javasdk.agent.task.TaskException.Failed} if the task failed, or {@link
   * akka.javasdk.agent.task.TaskException.Cancelled} if the task was cancelled.
   *
   * @param taskDefinition the task definition, used for typed result deserialization
   * @param <R> the result type of the task
   * @return the typed task result
   * @throws java.util.concurrent.CompletionException wrapping {@link
   *     akka.javasdk.agent.task.TaskException.Failed} or {@link
   *     akka.javasdk.agent.task.TaskException.Cancelled}
   */
  default <R> R result(TaskDefinition<R> taskDefinition) {
    return resultAsync(taskDefinition).toCompletableFuture().join();
  }

  /**
   * Async variant of {@link #result}.
   *
   * @param taskDefinition the task definition, used for typed result deserialization
   * @param <R> the result type of the task
   * @return a CompletionStage with the typed task result
   */
  <R> CompletionStage<R> resultAsync(TaskDefinition<R> taskDefinition);
}
