/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import akka.Done;
import akka.javasdk.agent.ConversationMemory.Event;
import akka.javasdk.agent.ConversationMemory.State;
import akka.javasdk.agent.ConversationMessage.AiMessage;
import akka.javasdk.agent.ConversationMessage.UserMessage;
import akka.javasdk.annotations.TypeName;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.annotations.ComponentId;
import com.typesafe.config.Config;
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
public final class ConversationMemory extends EventSourcedEntity<State, Event> {

  private static final Logger log = LoggerFactory.getLogger(ConversationMemory.class);

  private final Config config;

  public ConversationMemory(Config config) {
    this.config = config;
  }

  public record State(long maxLengthInBytes, long currentLengthInBytes, List<ConversationMessage> messages) {

    private static final Logger logger = LoggerFactory.getLogger(State.class);

    public State {
      if (maxLengthInBytes <= 0) throw new IllegalArgumentException("Maximum size must be greater than 0");
      messages = messages != null ? new LinkedList<>(messages) : new LinkedList<>();
      currentLengthInBytes = enforceMaxCapacity(messages, currentLengthInBytes, maxLengthInBytes);
    }

    public boolean isEmpty() {
      return messages.isEmpty();
    }

    public State withMaxSize(int newMaxSize) {
      List<ConversationMessage> updatedMessages = new LinkedList<>(messages);
      return new State(newMaxSize, currentLengthInBytes, updatedMessages);
    }

    public State addMessage(ConversationMessage message) {
      List<ConversationMessage> updatedMessages = new LinkedList<>(messages);
      updatedMessages.add(message);

      var updatedLengthSize = currentLengthInBytes + message.size();
      return new State(maxLengthInBytes, updatedLengthSize, updatedMessages);
    }

    public State clear() {
      return new State(maxLengthInBytes, 0, new LinkedList<>());
    }

    private static long enforceMaxCapacity(List<ConversationMessage> messages, long currentLengthSize, long maxSize) {
      var freedSpace = 0;
      while ((currentLengthSize - freedSpace) > maxSize) {
        // FIXME: delete also the reply from AI?
        freedSpace += messages.removeFirst().size();
        logger.debug("Removed oldest message. Remaining size={}, maxSizeInBytes={}", currentLengthSize, maxSize);
      }
      return currentLengthSize - freedSpace;
    }

  }
  
  @Override
  public State emptyState() {
    var maxSizeInBytes = config.getBytes("akka.javasdk.agent.memory.limited-window.max-size"); // 5Mb
    return new State(maxSizeInBytes, 0, new LinkedList<>());
  }

  /**
   * Sealed interface representing events that can occur in the ConversationMemory entity.
   */
  public sealed interface Event {

    @TypeName("akka-memory-limited-window-set")
    record LimitedWindowSet(int maxSizeInBytes, long ts) implements Event {
    }

    @TypeName("akka-memory-user-message-added")
    record UserMessageAdded(String componentId, String message, int tokens, long ts) implements Event {
    }

    @TypeName("akka-memory-ai-message-added")
    record AiMessageAdded(String componentId, String message, int tokens, long ts) implements Event {
    }
    
    @TypeName("akka-memory-deleted")
    record Deleted(long ts) implements Event {
    }
  }

  // Request commands
  public record LimitedWindow(int maxSizeInBytes) {}

  public Effect<Done> setLimitedWindow(LimitedWindow limitedWindow) {
    if (limitedWindow.maxSizeInBytes <= 0) {
      return effects().error("Maximum size must be greater than 0");
    } else {
      return effects()
          .persist(new Event.LimitedWindowSet(limitedWindow.maxSizeInBytes, System.currentTimeMillis()))
          .thenReply(__ -> done());
    }
  }

  public record AddInteractionCmd(String componentId, UserMessage userMessage, AiMessage aiMessage) { }
  public Effect<Done> addInteraction(AddInteractionCmd cmd) {
    var ts = System.currentTimeMillis();
    return effects()
        .persist(
            new Event.UserMessageAdded(cmd.componentId, cmd.userMessage.text(), cmd.userMessage.tokens(), ts),
            new Event.AiMessageAdded(cmd.componentId, cmd.aiMessage.text(),cmd.aiMessage.tokens(), ts))
        .thenReply(__ -> Done.done());
  }

  public ReadOnlyEffect<ConversationHistory> getHistory() {
    return effects().reply(
        new ConversationHistory(new LinkedList<>(currentState().messages)));
  }

  public Effect<Done> delete() {
    if (isDeleted()) {
      return effects().reply(done());
    } else {
      return effects()
          .persist(new Event.Deleted(System.currentTimeMillis()))
          .deleteEntity()
          .thenReply(__ -> done());
    }
  }

  @Override
  public State applyEvent(Event event) {
    return switch (event) {
      case Event.LimitedWindowSet limitedWindowSet ->
          currentState().withMaxSize(limitedWindowSet.maxSizeInBytes);
      case Event.UserMessageAdded userMsg ->
          currentState().addMessage(new UserMessage(userMsg.message(), userMsg.tokens()));
      case Event.AiMessageAdded aiMsg ->
          currentState().addMessage(new AiMessage(aiMsg.message(), aiMsg.tokens()));
      case Event.Deleted __ ->
          currentState().clear();
    };
  }
}
