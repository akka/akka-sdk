/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.autonomous;

import java.util.Optional;

/**
 * The status of a message that a client sent to an autonomous agent with {@link
 * akka.javasdk.client.AutonomousAgentClient#sendMessage(AgentMessage)}.
 */
public sealed interface MessageStatus {

  /**
   * The message is for the current task, and the model sees it in its next request for that task.
   *
   * @param taskId the id of the current task
   */
  record ForCurrentTask(String taskId) implements MessageStatus {}

  /**
   * The message waits until the task {@code taskId} starts, or until the next task starts when
   * {@code taskId} is empty.
   *
   * @param taskId the id of the task the message waits for, or empty for the next task
   */
  record Held(Optional<String> taskId) implements MessageStatus {}

  /**
   * The agent stored the outcome of the model call that saw the message. The ledger records that
   * model call under {@code interactionId}. Read it with {@link
   * akka.javasdk.ledger.LedgerClient#getInteraction(String)}. A new message never has this status.
   *
   * @param taskId the id of the task the message was for
   * @param interactionId the id of the model call that saw the message
   */
  record Consumed(String taskId, String interactionId) implements MessageStatus {}

  /**
   * The task ended before the agent consumed the message. A new message never has this status.
   *
   * @param taskId the id of the task the message was for
   */
  record Dropped(String taskId) implements MessageStatus {}

  /**
   * The agent rejected the message and did not store it.
   *
   * @param reason why the agent rejected the message
   */
  record Rejected(String reason) implements MessageStatus {}
}
