/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import akka.javasdk.agent.MessageContent.TextMessageContent;
import java.util.List;

/**
 * Represents a user message that can contain multimodal content for interaction with AI models.
 *
 * <p>A {@code UserMessage} encapsulates one or more {@link MessageContent} elements, allowing
 * agents to send text, images, or both.
 *
 * <p>Multimodal message with text and image:
 *
 * <pre>{@code
 * UserMessage message = UserMessage.from(
 *     MessageContent.TextMessageContent.from("What's in this image?"),
 *     ImageMessageContent.from("https://example.com/photo.jpg")
 * );
 * }</pre>
 *
 * @param contents The list of message content elements (text, images, etc.)
 * @see MessageContent
 * @see Agent.Effect.Builder#userMessage(UserMessage)
 */
public record UserMessage(List<MessageContent> contents) {

  public UserMessage {
    MessageContent.requireSendableAsInput(contents);
  }

  /** Whether this message consists of a single {@link TextMessageContent} and nothing else. */
  public boolean isTextOnly() {
    return contents.size() == 1 && contents.get(0) instanceof TextMessageContent;
  }

  /**
   * The text of this message.
   *
   * @throws ClassCastException if the first content element is not a {@link TextMessageContent}
   */
  public String text() {
    return ((TextMessageContent) contents.get(0)).text();
  }

  /** Create a text-only message. */
  public static UserMessage from(String text) {
    return new UserMessage(List.of(TextMessageContent.from(text)));
  }

  /** Create a multimodal message from one or more content elements. */
  public static UserMessage from(MessageContent... messageContent) {
    return new UserMessage(List.of(messageContent));
  }
}
