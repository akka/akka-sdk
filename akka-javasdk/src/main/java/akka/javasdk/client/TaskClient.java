/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.client;

import akka.annotation.DoNotInherit;
import akka.javasdk.agent.task.Task;
import akka.javasdk.agent.task.TaskSnapshot;
import java.util.concurrent.CompletionStage;

/**
 * Client for creating and querying tasks.
 *
 * <p>Not for user extension or instantiation, returned by the SDK component client.
 *
 * @param <R> The result type of the task, determined by the task definition.
 */
@DoNotInherit
public interface TaskClient<R> {

  /**
   * Create a task entity and return its ID. The task can then be assigned to an agent via {@link
   * AutonomousAgentClient#assignTasks}.
   *
   * @param task the task to create, with instructions
   * @return the new task ID
   */
  default String create(Task<R> task) {
    return createAsync(task).toCompletableFuture().join();
  }

  /**
   * Async variant of {@link #create}.
   *
   * @param task the task to create, with instructions
   * @return a CompletionStage with the new task ID
   */
  CompletionStage<String> createAsync(Task<R> task);

  /**
   * Get a point-in-time snapshot of the task's current state.
   *
   * @param taskId the task ID
   * @return the task snapshot containing status and typed result
   */
  default TaskSnapshot<R> get(String taskId) {
    return getAsync(taskId).toCompletableFuture().join();
  }

  /**
   * Async variant of {@link #get}.
   *
   * @param taskId the task ID
   * @return a CompletionStage with the task snapshot containing status and typed result
   */
  CompletionStage<TaskSnapshot<R>> getAsync(String taskId);
}
