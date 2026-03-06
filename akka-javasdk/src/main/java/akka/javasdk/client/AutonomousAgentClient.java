/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.client;

import akka.Done;
import akka.annotation.DoNotInherit;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.task.Task;
import java.util.concurrent.CompletionStage;

/**
 * Client for interacting with an {@link AutonomousAgent}.
 *
 * <p>Not for user extension or instantiation, returned by the SDK component client.
 */
@DoNotInherit
public interface AutonomousAgentClient {

  /**
   * Create a task, assign it to a fresh agent instance, and automatically stop the agent when done.
   * Each call spins up an independent agent workflow. Returns the task ID for later status checks
   * via {@link TaskClient}.
   *
   * @param task the task to execute
   * @return the task ID
   */
  default String runSingleTask(Task<?> task) {
    return runSingleTaskAsync(task).toCompletableFuture().join();
  }

  /**
   * Async variant of {@link #runSingleTask}.
   *
   * @param task the task to execute
   * @return a CompletionStage with the task ID
   */
  CompletionStage<String> runSingleTaskAsync(Task<?> task);

  /**
   * Assign one or more previously created tasks to this agent. Tasks are queued if the agent is
   * busy.
   *
   * @param taskIds IDs of previously created tasks
   */
  default void assignTasks(String... taskIds) {
    assignTasksAsync(taskIds).toCompletableFuture().join();
  }

  /**
   * Async variant of {@link #assignTasks}.
   *
   * @param taskIds IDs of previously created tasks
   */
  CompletionStage<Done> assignTasksAsync(String... taskIds);

  /** Stop the agent. */
  default void stop() {
    stopAsync().toCompletableFuture().join();
  }

  /** Async variant of {@link #stop}. */
  CompletionStage<Done> stopAsync();
}
