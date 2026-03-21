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
 * Client for creating and querying tasks, bound to a specific task entity ID.
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
}
