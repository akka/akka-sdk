/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.client;

import akka.Done;
import akka.NotUsed;
import akka.annotation.DoNotInherit;
import akka.javasdk.agent.autonomous.AgentMessage;
import akka.javasdk.agent.autonomous.AgentSetup;
import akka.javasdk.agent.autonomous.AgentState;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.autonomous.MessageAcknowledgement;
import akka.javasdk.agent.autonomous.MessageStatus;
import akka.javasdk.agent.autonomous.Notification;
import akka.javasdk.agent.task.Task;
import akka.javasdk.impl.ErrorHandling;
import akka.stream.javadsl.Source;
import java.util.concurrent.CompletionException;
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
   * Each call starts an independent agent instance. Returns the task ID for later status checks via
   * {@link TaskClient}.
   *
   * @param task the task to execute
   * @return the task ID
   */
  default String runSingleTask(Task<?> task) {
    try {
      return runSingleTaskAsync(task).toCompletableFuture().join();
    } catch (CompletionException e) {
      throw ErrorHandling.unwrapCompletionException(e);
    }
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
    try {
      assignTasksAsync(taskIds).toCompletableFuture().join();
    } catch (CompletionException e) {
      throw ErrorHandling.unwrapCompletionException(e);
    }
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
    try {
      setupAsync(setup).toCompletableFuture().join();
    } catch (CompletionException e) {
      throw ErrorHandling.unwrapCompletionException(e);
    }
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
    try {
      return getStateAsync().toCompletableFuture().join();
    } catch (CompletionException e) {
      throw ErrorHandling.unwrapCompletionException(e);
    }
  }

  /** Async variant of {@link #getState}. */
  CompletionStage<AgentState> getStateAsync();

  /**
   * Send a message to this agent instance while it works on its tasks. The message is for the task
   * set with {@link AgentMessage#taskId(String)}, or else for the current task, or for the next
   * task that starts when no task runs. The model sees it in its next request for that task, at the
   * start of an iteration or after the tool calls of a round. While a tool call runs, for example a
   * delegation that waits for its workers, the message waits for it.
   *
   * <p>The acknowledgement says where the message went, or why the agent rejected it. The agent
   * does not store a rejected message. The agent drops a message whose task ends before the message
   * is consumed, see {@link MessageStatus.Consumed}. {@link #getState()} and the {@link
   * Notification.MessagesConsumed} and {@link Notification.MessagesDropped} notifications report
   * what happened to it.
   *
   * <p>Set {@link AgentMessage#messageId(String)} to make a retry safe. A retry with the same id
   * gets the status of the first message, and the agent stores nothing new.
   *
   * <p>Fails when the agent instance does not exist or is stopped. An instance exists after its
   * first setup or task.
   *
   * @param message the message
   * @return where the message went
   */
  default MessageAcknowledgement sendMessage(AgentMessage message) {
    try {
      return sendMessageAsync(message).toCompletableFuture().join();
    } catch (CompletionException e) {
      throw ErrorHandling.unwrapCompletionException(e);
    }
  }

  /**
   * Async variant of {@link #sendMessage}.
   *
   * @param message the message
   * @return a CompletionStage with where the message went
   */
  CompletionStage<MessageAcknowledgement> sendMessageAsync(AgentMessage message);

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
    try {
      suspendAsync(reason).toCompletableFuture().join();
    } catch (CompletionException e) {
      throw ErrorHandling.unwrapCompletionException(e);
    }
  }

  /** Async variant of {@link #suspend()}. */
  default CompletionStage<Done> suspendAsync() {
    return suspendAsync(null);
  }

  /** Async variant of {@link #suspend(String)}. */
  CompletionStage<Done> suspendAsync(String reason);

  /** Resume a previously suspended agent. */
  default void resume() {
    try {
      resumeAsync().toCompletableFuture().join();
    } catch (CompletionException e) {
      throw ErrorHandling.unwrapCompletionException(e);
    }
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
    try {
      terminateAsync(reason).toCompletableFuture().join();
    } catch (CompletionException e) {
      throw ErrorHandling.unwrapCompletionException(e);
    }
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
