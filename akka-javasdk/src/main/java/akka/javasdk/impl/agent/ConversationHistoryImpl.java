/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent;

import akka.annotation.InternalApi;
import akka.javasdk.agent.ConversationHistory;
import akka.javasdk.agent.ConversationMessage;
import akka.javasdk.annotations.TypeName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;

/**
 * INTERNAL USE
 * Not for user extension or instantiation
 */
@InternalApi
public record ConversationHistoryImpl(int maxSize, List<ConversationMessage> messages) implements ConversationHistory {

  private static final Logger logger = LoggerFactory.getLogger(ConversationHistoryImpl.class);

  public ConversationHistoryImpl {
    if (maxSize <= 0) throw new IllegalArgumentException("Maximum size must be greater than 0");
    messages = messages != null ? new LinkedList<>(messages) : new LinkedList<>();
    enforceMaxCapacity(messages, maxSize);
  }

  public boolean isEmpty() {
    return messages.isEmpty();
  }


  public ConversationHistoryImpl withMaxSize(int newMaxSize) {
    List<ConversationMessage> updatedMessages = new LinkedList<>(messages);
    return new ConversationHistoryImpl(newMaxSize, updatedMessages);
  }

  public ConversationHistoryImpl addMessage(ConversationMessage message) {
    List<ConversationMessage> updatedMessages = new LinkedList<>(messages);
    updatedMessages.add(message);

    return new ConversationHistoryImpl(maxSize, updatedMessages);
  }

  public ConversationHistoryImpl clear() {
    return new ConversationHistoryImpl(maxSize, new LinkedList<>());
  }

  private static void enforceMaxCapacity(List<ConversationMessage> messages, int maxSize) {

    // If we exceed the maximum size, remove the oldest message
    while (messages.size() > maxSize) {
      logger.debug("Erasing oldest message from history, remaining={}, maxSize={}", messages.size() - 1, maxSize);
      messages.removeFirst();
    }
  }

}