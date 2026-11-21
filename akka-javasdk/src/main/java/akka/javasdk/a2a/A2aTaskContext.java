/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.a2a;

/**
 * The A2A task that a workflow answers. Inject it into the constructor of a workflow that publishes
 * a long skill with {@link akka.javasdk.annotations.a2a.A2aSkill}. The workflow id is the task id.
 * The workflow must be published by the A2A endpoint with {@link A2aAgents#taskWorkflow(Class)};
 * the service fails at startup when another workflow takes an {@code A2aTaskContext}.
 *
 * <p>Each method records the progress in the task that A2A clients read with {@code GetTask}, and
 * sends it to the push notification configurations of the task. Call the methods from a step, where
 * calls to other components belong; each method returns when the task is stored.
 *
 * <p>Reporting the same artifact id or the same message id again replaces the earlier one, so a
 * step that is retried does not add the same output twice.
 *
 * <p>{@link #inputRequired(String)} only changes what the client sees. Pause the workflow in the
 * same step with {@code stepEffects().thenPause()}. The next message the client sends on the task
 * reaches the {@code onMessage} command handler of the workflow.
 */
public interface A2aTaskContext {

  /** The id of the task, which is also the workflow id. */
  String taskId();

  /** The id of the conversation the task belongs to. */
  String contextId();

  /**
   * Reports that the agent is working on the task.
   *
   * @param text a short description of the current work, shown to the client
   */
  void working(String text);

  /**
   * Adds an output to the task. An artifact with the same id replaces the earlier one.
   *
   * @param artifact the output
   */
  void artifact(A2aArtifact artifact);

  /**
   * Asks the client for more input. The task shows the state input-required with the question.
   * Pause the workflow after calling this.
   *
   * @param question the question for the client
   */
  void inputRequired(String question);

  /**
   * Completes the task. The task can no longer change.
   *
   * @param message the final message of the agent, added to the history of the task
   */
  void completed(A2aMessage message);

  /**
   * Completes the task with a text message. The task can no longer change.
   *
   * @param text the final message of the agent
   */
  void completed(String text);

  /**
   * Fails the task. The task can no longer change.
   *
   * @param reason why the task failed, shown to the client
   */
  void failed(String reason);

  /**
   * Answers the first message of the task with a message instead of a task. {@code SendMessage}
   * then returns the message, and the task is recorded as completed. Only the answer to the first
   * message can be a message. End the workflow after calling this.
   *
   * @param message the answer
   */
  void respondWithMessage(A2aMessage message);

  /**
   * A message from the agent with one text part, in the context and task of this context.
   *
   * @param text the text of the message
   */
  A2aMessage agentMessage(String text);
}
