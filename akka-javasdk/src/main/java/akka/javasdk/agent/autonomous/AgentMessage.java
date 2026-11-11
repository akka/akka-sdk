/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.autonomous;

import akka.javasdk.agent.MessageContent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A message from a client to a running autonomous agent: text with optional attachments, for one
 * task of the agent. Send it with {@link
 * akka.javasdk.client.AutonomousAgentClient#sendMessage(AgentMessage)}.
 *
 * <p>Each method that sets a value returns a new instance and leaves this one unchanged.
 */
public final class AgentMessage {

  private final String text;
  private final List<MessageContent> attachments;
  private final Optional<String> messageId;
  private final Optional<String> taskId;

  private AgentMessage(
      String text,
      List<MessageContent> attachments,
      Optional<String> messageId,
      Optional<String> taskId) {
    this.text = text;
    this.attachments = attachments;
    this.messageId = messageId;
    this.taskId = taskId;
  }

  /**
   * Create a message with the given text.
   *
   * @param text the text for the model
   * @throws IllegalArgumentException if the text is null
   */
  public static AgentMessage of(String text) {
    if (text == null) {
      throw new IllegalArgumentException("The text of a message must not be null");
    }
    return new AgentMessage(text, List.of(), Optional.empty(), Optional.empty());
  }

  /** The text of this message. */
  public String text() {
    return text;
  }

  /** Content attached to this message (images, PDFs), or an empty list. */
  public List<MessageContent> attachments() {
    return attachments;
  }

  /**
   * Return a new message with the given content attached. Images and PDFs must be referenced by
   * URI, with {@link MessageContent.ImageUrlMessageContent} or {@link
   * MessageContent.PdfUrlMessageContent}.
   *
   * @throws IllegalArgumentException if a content item carries inline bytes
   */
  public AgentMessage attach(MessageContent... content) {
    var toAttach = List.of(content);
    MessageContent.requireSendableAsInput(toAttach);
    var updated = new ArrayList<>(this.attachments);
    updated.addAll(toAttach);
    return new AgentMessage(this.text, List.copyOf(updated), this.messageId, this.taskId);
  }

  /** The id of this message, if set. */
  public Optional<String> messageId() {
    return messageId;
  }

  /**
   * Return a new message with the given id. While the first message is pending or among {@link
   * AgentState#recentMessages()}, a retry with the same id gets the status of the first message,
   * and the agent stores nothing new. The agent does not store a rejected message, so a retry of it
   * is a new message. Without an id, the runtime generates one, and a retry is a new message.
   *
   * @throws IllegalArgumentException if the id is null or empty
   */
  public AgentMessage messageId(String messageId) {
    requireNonEmpty(messageId, "message id");
    return new AgentMessage(this.text, this.attachments, Optional.of(messageId), this.taskId);
  }

  /** The id of the task this message is for, if set. */
  public Optional<String> taskId() {
    return taskId;
  }

  /**
   * Return a new message for the task with the given id. The task must be assigned to the agent.
   * The agent rejects a message for a task that ended or that is not assigned to it. Without a task
   * id, the message is for the current task, or for the next task that starts when no task runs.
   *
   * @throws IllegalArgumentException if the id is null or empty
   */
  public AgentMessage taskId(String taskId) {
    requireNonEmpty(taskId, "task id");
    return new AgentMessage(this.text, this.attachments, this.messageId, Optional.of(taskId));
  }

  private static void requireNonEmpty(String value, String name) {
    if (value == null || value.isEmpty()) {
      throw new IllegalArgumentException("The " + name + " of a message must not be null or empty");
    }
  }
}
