/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import akka.Done;
import akka.javasdk.agent.ConversationMemory.History;
import akka.javasdk.agent.ConversationMemory.Event;
import akka.javasdk.annotations.TypeName;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.annotations.ComponentId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;

import static akka.Done.done;

/**
 * ConversationMemory is an EventSourcedEntity that maintains a limited history of conversation
 * messages in a FIFO (First In, First Out) style.
 * <p>
 * The maximum number of entries in the history can be set dynamically with command setLimitedWindow.
 * {@link akka.javasdk.client.ComponentClient} can be used to interact directly with this entity.
 */
@ComponentId("akka-conversation-memory")
public final class ConversationMemory extends EventSourcedEntity<History, Event> {

  private static final Logger log = LoggerFactory.getLogger(ConversationMemory.class);

  @TypeName("akka-history")
  public record History(int maxSize, List<Message> messages) {

    private static final Logger logger = LoggerFactory.getLogger(History.class);

    public History {
      if (maxSize <= 0) throw new IllegalArgumentException("Maximum size must be greater than 0");
      messages = messages != null ? new LinkedList<>(messages) : new LinkedList<>();
      enforceMaxCapacity(messages, maxSize);
    }

    public int size() {
      return messages.size();
    }

    public boolean isEmpty() {
      return messages.isEmpty();
    }

    public History withMaxSize(int newMaxSize) {
      List<Message> updatedMessages = new LinkedList<>(messages);
      return new History(newMaxSize, updatedMessages);
    }

    public History addMessage(Message message) {
      List<Message> updatedMessages = new LinkedList<>(messages);
      updatedMessages.add(message);

      return new History(maxSize, updatedMessages);
    }

    private static void enforceMaxCapacity(List<Message> messages, int maxSize) {

      // If we exceed the maximum size, remove the oldest message
      while (messages.size() > maxSize) {
        logger.debug("Erasing oldest message from history, remaining={}, maxSize={}", messages.size() - 1, maxSize);
        messages.removeFirst();
      }
    }
    
    /**
     * Clears all messages from the history.
     * 
     * @return A new History with no messages
     */
    public History clear() {
      return new History(maxSize, new LinkedList<>());
    }
  }

  @Override
  public History emptyState() {
    // FIXME: load default from config?
    return new History(1000, new LinkedList<>());
  }

  /**
   * Sealed interface representing events that can occur in the ConversationMemory entity.
   */
  public sealed interface Event {

    @TypeName("akka-memory-limited-window-set")
    record LimitedWindowSet(int maxSize) implements Event {
    }

    @TypeName("akka-memory-user-message-added")
    record UserMessageAdded(String message) implements Event {
    }

    @TypeName("akka-memory-ai-message-added")
    record AiMessageAdded(String message) implements Event {
    }
    
    @TypeName("akka-memory-deleted")
    record Deleted() implements Event {
    }
  }

  // Request commands
  public record LimitedWindow(int maxSize) {}

  public Effect<Done> setLimitedWindow(LimitedWindow limitedWindow) {
    if (limitedWindow.maxSize <= 0) {
      return effects().error("Maximum size must be greater than 0");
    } else {
      return effects()
          .persist(new Event.LimitedWindowSet(limitedWindow.maxSize))
          .thenReply(__ -> done());
    }
  }

  public Effect<Done> addUserMessage(String message) {
    log.debug("Adding user message: {}", message);
    return effects()
        .persist(new Event.UserMessageAdded(message))
        .thenReply(__ -> Done.done());
  }

  public Effect<Done> addAiMessage(String message) {
    return effects()
        .persist(new Event.AiMessageAdded(message))
        .thenReply(__ -> Done.done());
  }

  public record AddInteractionCmd(String userMessage, String aiMessage) {

  }
  public Effect<Done> addInteraction(AddInteractionCmd cmd) {
    log.debug("Adding interaction: user={}, ai={}", cmd.userMessage, cmd.aiMessage);
    return effects()
        .persistAll(List.of(new Event.UserMessageAdded(cmd.userMessage), new Event.AiMessageAdded(cmd.aiMessage)))
        .thenReply(__ -> Done.done());
  }

  public ReadOnlyEffect<History> getHistory() {
    return effects().reply(currentState());
  }

  public Effect<Done> delete() {
    if (isDeleted()) {
      return effects().reply(done());
    } else {
      return effects()
          .persist(new Event.Deleted())
          .deleteEntity()
          .thenReply(__ -> done());
    }
  }

  @Override
  public History applyEvent(Event event) {
    return switch (event) {
      case Event.LimitedWindowSet limitedWindowSet ->
          currentState().withMaxSize(limitedWindowSet.maxSize());
      case Event.UserMessageAdded userMsg ->
          currentState().addMessage(UserMessage.of(userMsg.message()));
      case Event.AiMessageAdded aiMsg ->
          currentState().addMessage(AiMessage.of(aiMsg.message()));
      case Event.Deleted __ ->
          currentState().clear();
    };
  }
}
