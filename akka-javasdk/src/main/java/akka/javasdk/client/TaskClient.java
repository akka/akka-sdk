/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.client;

import akka.Done;
import akka.NotUsed;
import akka.annotation.DoNotInherit;
import akka.javasdk.agent.task.Task;
import akka.javasdk.agent.task.TaskDefinition;
import akka.javasdk.agent.task.TaskNotification;
import akka.javasdk.agent.task.TaskSnapshot;
import akka.javasdk.impl.ErrorHandling;
import akka.stream.javadsl.Source;
import java.util.concurrent.CompletionException;
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
    try {
      return createAsync(task).toCompletableFuture().join();
    } catch (CompletionException e) {
      throw ErrorHandling.unwrapCompletionException(e);
    }
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
    try {
      assignAsync(assignee).toCompletableFuture().join();
    } catch (CompletionException e) {
      throw ErrorHandling.unwrapCompletionException(e);
    }
  }

  /**
   * Async variant of {@link #assign}.
   *
   * @param assignee identifier of the owner (e.g., a user ID, team name, or process identifier)
   * @return a CompletionStage that completes when the task has been assigned
   */
  CompletionStage<Done> assignAsync(String assignee);

  /**
   * Complete a task with a typed result. The task must be assigned first (via {@link #assign} or by
   * the agent runtime). For tasks not yet in progress, completion is allowed directly from the
   * assigned state — no separate start step is needed.
   *
   * @param taskDefinition the task definition, used for result type validation
   * @param result the task result
   * @param <R> the result type of the task
   */
  default <R> void complete(TaskDefinition<R> taskDefinition, R result) {
    try {
      completeAsync(taskDefinition, result).toCompletableFuture().join();
    } catch (CompletionException e) {
      throw ErrorHandling.unwrapCompletionException(e);
    }
  }

  /**
   * Async variant of {@link #complete}.
   *
   * @param taskDefinition the task definition, used for result type validation
   * @param result the task result
   * @param <R> the result type of the task
   * @return a CompletionStage that completes when the task has been completed
   */
  <R> CompletionStage<Done> completeAsync(TaskDefinition<R> taskDefinition, R result);

  /**
   * Fail a task with a reason. The task must be assigned first (via {@link #assign} or by the agent
   * runtime).
   *
   * @param reason the failure reason
   */
  default void fail(String reason) {
    try {
      failAsync(reason).toCompletableFuture().join();
    } catch (CompletionException e) {
      throw ErrorHandling.unwrapCompletionException(e);
    }
  }

  /**
   * Async variant of {@link #fail}.
   *
   * @param reason the failure reason
   * @return a CompletionStage that completes when the task has been failed
   */
  CompletionStage<Done> failAsync(String reason);

  // --- querying ---

  /**
   * Get a point-in-time snapshot of the task's current state.
   *
   * <p>The supplied {@link TaskDefinition} is validated against the task entity: if its name or
   * result type does not match the definition the task was created with, {@link
   * akka.javasdk.agent.task.TaskException.TypeMismatch} is thrown. This guards against accidentally
   * reading a task with the wrong definition (for example, calling {@code
   * forTask(taskId).get(OtherTasks.SOMETHING_ELSE)} on an id that was created with a different
   * task) and ensures the returned result can be safely deserialized as {@code R}.
   *
   * @param taskDefinition the task definition, used for typed result deserialization
   * @param <R> the result type of the task
   * @return the task snapshot containing status and typed result
   * @throws akka.javasdk.agent.task.TaskException.TypeMismatch if the task's name or result type
   *     does not match {@code taskDefinition}
   */
  default <R> TaskSnapshot<R> get(TaskDefinition<R> taskDefinition) {
    try {
      return getAsync(taskDefinition).toCompletableFuture().join();
    } catch (CompletionException e) {
      throw ErrorHandling.unwrapCompletionException(e);
    }
  }

  /**
   * Async variant of {@link #get}.
   *
   * <p>The returned CompletionStage fails with {@link
   * akka.javasdk.agent.task.TaskException.TypeMismatch} if the task's name or result type does not
   * match {@code taskDefinition}.
   *
   * @param taskDefinition the task definition, used for typed result deserialization
   * @param <R> the result type of the task
   * @return a CompletionStage with the task snapshot containing status and typed result
   */
  <R> CompletionStage<TaskSnapshot<R>> getAsync(TaskDefinition<R> taskDefinition);

  /**
   * Blocks until the task reaches a terminal state and returns the typed result.
   *
   * <p>As with {@link #get}, the supplied {@link TaskDefinition} is validated against the task
   * entity.
   *
   * @param taskDefinition the task definition, used for typed result deserialization
   * @param <R> the result type of the task
   * @return the typed task result
   * @throws akka.javasdk.agent.task.TaskException.Failed if the task failed
   * @throws akka.javasdk.agent.task.TaskException.Cancelled if the task was cancelled
   * @throws akka.javasdk.agent.task.TaskException.TypeMismatch if the task's name or result type
   *     does not match {@code taskDefinition}
   */
  default <R> R result(TaskDefinition<R> taskDefinition) {
    try {
      return resultAsync(taskDefinition).toCompletableFuture().join();
    } catch (CompletionException e) {
      throw ErrorHandling.unwrapCompletionException(e);
    }
  }

  /**
   * Async variant of {@link #result}.
   *
   * <p>The returned CompletionStage fails with {@link akka.javasdk.agent.task.TaskException.Failed}
   * if the task failed, {@link akka.javasdk.agent.task.TaskException.Cancelled} if it was
   * cancelled, or {@link akka.javasdk.agent.task.TaskException.TypeMismatch} if the task's name or
   * result type does not match {@code taskDefinition}.
   *
   * @param taskDefinition the task definition, used for typed result deserialization
   * @param <R> the result type of the task
   * @return a CompletionStage with the typed task result
   */
  <R> CompletionStage<R> resultAsync(TaskDefinition<R> taskDefinition);

  /**
   * Subscribe to notifications published by the task entity. See {@link TaskNotification} for the
   * event catalog.
   *
   * @return a source of task notification events
   */
  Source<TaskNotification, NotUsed> notificationStream();
}
