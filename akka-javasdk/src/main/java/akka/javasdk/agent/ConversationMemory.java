/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import akka.Done;
import akka.javasdk.agent.ConversationMemory.Event;
import akka.javasdk.annotations.TypeName;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.impl.agent.ConversationHistoryImpl;
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
public final class ConversationMemory extends EventSourcedEntity<ConversationHistory, Event> {

  private static final Logger log = LoggerFactory.getLogger(ConversationMemory.class);

  @Override
  public ConversationHistory emptyState() {
    // FIXME: load default from config?
    return new ConversationHistoryImpl(1000, new LinkedList<>());
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

  public ReadOnlyEffect<ConversationHistory> getHistory() {
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
  public ConversationHistory applyEvent(Event event) {
    // we are in control here, so this should be safe
    var currentState = (ConversationHistoryImpl) currentState();

    return switch (event) {
      case Event.LimitedWindowSet limitedWindowSet ->
          currentState.withMaxSize(limitedWindowSet.maxSize);
      case Event.UserMessageAdded userMsg ->
          currentState.addMessage(new UserMessage(userMsg.message()));
      case Event.AiMessageAdded aiMsg ->
          currentState.addMessage(new AiMessage(aiMsg.message()));
      case Event.Deleted __ ->
          currentState.clear();
    };
  }
}
