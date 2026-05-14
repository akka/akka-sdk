/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.client;

import akka.Done;
import akka.NotUsed;
import akka.annotation.DoNotInherit;
import akka.javasdk.agent.autonomous.AgentSetup;
import akka.javasdk.agent.autonomous.AgentState;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.autonomous.Notification;
import akka.javasdk.agent.task.Task;
import akka.stream.javadsl.Source;
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

  /**
   * Apply per-instance configuration to this agent. The instructions override the static
   * instructions from the agent's definition, and capabilities extend the static capabilities.
   *
   * @param setup the per-instance configuration
   */
  default void setup(AgentSetup setup) {
    setupAsync(setup).toCompletableFuture().join();
  }

  /**
   * Async variant of {@link #setup}.
   *
   * @param setup the per-instance configuration
   */
  CompletionStage<Done> setupAsync(AgentSetup setup);

  /**
   * Get the current state of this agent instance.
   *
   * @return a summary of the agent's current state
   */
  default AgentState getState() {
    return getStateAsync().toCompletableFuture().join();
  }

  /** Async variant of {@link #getState}. */
  CompletionStage<AgentState> getStateAsync();

  /** Suspend the agent. A suspended agent stops iterating until resumed. */
  default void suspend() {
    suspend(null);
  }

  /**
   * Suspend the agent with a reason. A suspended agent stops iterating until resumed.
   *
   * @param reason a description of why the agent is being suspended
   */
  default void suspend(String reason) {
    suspendAsync(reason).toCompletableFuture().join();
  }

  /** Async variant of {@link #suspend()}. */
  default CompletionStage<Done> suspendAsync() {
    return suspendAsync(null);
  }

  /** Async variant of {@link #suspend(String)}. */
  CompletionStage<Done> suspendAsync(String reason);

  /** Resume a previously suspended agent. */
  default void resume() {
    resumeAsync().toCompletableFuture().join();
  }

  /** Async variant of {@link #resume}. */
  CompletionStage<Done> resumeAsync();

  /** Terminate the agent. */
  default void terminate() {
    terminate(null);
  }

  /**
   * Terminate the agent with a reason.
   *
   * @param reason a description of why the agent is being terminated
   */
  default void terminate(String reason) {
    terminateAsync(reason).toCompletableFuture().join();
  }

  /** Async variant of {@link #terminate()}. */
  default CompletionStage<Done> terminateAsync() {
    return terminateAsync(null);
  }

  /** Async variant of {@link #terminate(String)}. */
  CompletionStage<Done> terminateAsync(String reason);

  /**
   * Subscribe to lifecycle notifications for this agent instance. Notifications are published by
   * the runtime as the agent progresses through its execution loop.
   *
   * <p>The returned source emits events such as {@link Notification.Activated}, {@link
   * Notification.IterationStarted}, {@link Notification.IterationCompleted}, and {@link
   * Notification.Stopped}.
   *
   * @return a source of notification events
   */
  Source<Notification, NotUsed> notificationStream();
}
