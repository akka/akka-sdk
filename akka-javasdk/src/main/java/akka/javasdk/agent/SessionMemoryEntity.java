/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import akka.Done;
import akka.javasdk.agent.SessionMemoryEntity.Event;
import akka.javasdk.agent.SessionMemoryEntity.State;
import akka.javasdk.agent.SessionMessage.AiMessage;
import akka.javasdk.agent.SessionMessage.UserMessage;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.TypeName;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import static akka.Done.done;

/**
 * SessionMemory is an EventSourcedEntity that maintains a limited history of conversation
 * messages in a FIFO (First In, First Out) style.
 * <p>
 * The maximum number of entries in the history can be set dynamically with command setLimitedWindow.
 * {@link akka.javasdk.client.ComponentClient} can be used to interact directly with this entity.
 */
@ComponentId("akka-session-memory")
public final class SessionMemoryEntity extends EventSourcedEntity<State, Event> {

  private static final Logger log = LoggerFactory.getLogger(SessionMemoryEntity.class);

  private final Config config;

  public SessionMemoryEntity(Config config) {
    this.config = config;
  }

  public record State(long maxLengthInBytes, long currentLengthInBytes, List<SessionMessage> messages,
                      long totalTokenUsage) {

    private static final Logger logger = LoggerFactory.getLogger(State.class);

    public State {
      if (maxLengthInBytes <= 0) throw new IllegalArgumentException("Maximum size must be greater than 0");
      messages = messages != null ? messages : Collections.emptyList();
      currentLengthInBytes = enforceMaxCapacity(messages, currentLengthInBytes, maxLengthInBytes);
    }

    public boolean isEmpty() {
      return messages.isEmpty();
    }

    public State withMaxSize(int newMaxSize) {
      return new State(newMaxSize, currentLengthInBytes, messages, totalTokenUsage);
    }

    public State addMessage(SessionMessage message) {
      // avoid copies of the list for efficiency, need to be careful not to return the list ref to outside
      messages.add(message);

      var updatedLengthSize = currentLengthInBytes + message.size();
      return new State(maxLengthInBytes, updatedLengthSize, messages, totalTokenUsage);
    }

    public State withTotalTokenUsage(long totalTokens) {
      return new State(maxLengthInBytes, currentLengthInBytes, messages, totalTokens);
    }

    public State clear() {
      return new State(maxLengthInBytes, 0, new LinkedList<>(), 0);
    }

    private static long enforceMaxCapacity(List<SessionMessage> messages, long currentLengthSize, long maxSize) {
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
    var maxSizeInBytes = config.getBytes("akka.javasdk.agent.memory.limited-window.max-size");
    return new State(maxSizeInBytes, 0, new LinkedList<>(), 0L);
  }

  /**
   * Sealed interface representing events that can occur in the SessionMemory entity.
   */
  public sealed interface Event {

    @TypeName("akka-memory-limited-window-set")
    record LimitedWindowSet(long timestamp, int maxSizeInBytes) implements Event {
    }

    @TypeName("akka-memory-user-message-added")
    record UserMessageAdded(long timestamp, String componentId, String message, int tokens,
                            long totalTokenUsage) implements Event {
    }

    @TypeName("akka-memory-ai-message-added")
    record AiMessageAdded(long timestamp,
                          String componentId,
                          String message,
                          int tokens,
                          long totalTokenUsage,
                          List<SessionMessage.ToolCallInteraction> toolCallInteraction) implements Event {

    }

    @TypeName("akka-memory-deleted")
    record Deleted(long timestamp) implements Event {
    }
  }

  // Request commands
  public record LimitedWindow(int maxSizeInBytes) {
  }

  public Effect<Done> setLimitedWindow(LimitedWindow limitedWindow) {
    if (limitedWindow.maxSizeInBytes <= 0) {
      return effects().error("Maximum size must be greater than 0");
    } else {
      return effects()
        .persist(new Event.LimitedWindowSet(System.currentTimeMillis(), limitedWindow.maxSizeInBytes))
        .thenReply(__ -> done());
    }
  }

  public record AddInteractionCmd(String componentId, UserMessage userMessage, AiMessage aiMessage) {
  }

  public Effect<Done> addInteraction(AddInteractionCmd cmd) {
    var totalTokensUser = currentState().totalTokenUsage + cmd.userMessage.tokens();
    var totalTokensAi = totalTokensUser + cmd.aiMessage.tokens();

    var toolInteractions = cmd.aiMessage.toolCallInteractions();

    return effects()
        .persist(
            new Event.UserMessageAdded(cmd.userMessage.timestamp(), cmd.componentId, cmd.userMessage.text(), cmd.userMessage.tokens(), totalTokensUser),
            new Event.AiMessageAdded(cmd.aiMessage.timestamp(), cmd.componentId, cmd.aiMessage.text(),cmd.aiMessage.tokens(), totalTokensAi, toolInteractions))
        .thenReply(__ -> Done.done());
  }

  public record GetHistoryCmd(Optional<Integer> lastNMessages) {
  }

  public ReadOnlyEffect<SessionHistory> getHistory(GetHistoryCmd cmd) {
    List<SessionMessage> messages = currentState().messages();
    if (cmd.lastNMessages != null
      && cmd.lastNMessages.isPresent()
      && messages.size() > cmd.lastNMessages.get()) {
      var lastN = messages
        .subList(messages.size() - cmd.lastNMessages.get(), messages.size());
      // make sure this returns a copy of the list and not the list itself
      return effects().reply(
        new SessionHistory(new LinkedList<>(lastN)));
    } else {
      // make sure this returns a copy of the list and not the list itself
      return effects().reply(
        new SessionHistory(new LinkedList<>(messages)));
    }
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
          currentState()
              .addMessage(new UserMessage(userMsg.timestamp(), userMsg.message(), userMsg.tokens()))
              .withTotalTokenUsage(userMsg.totalTokenUsage());
      case Event.AiMessageAdded aiMsg ->
          currentState()
              .addMessage(new AiMessage(aiMsg.timestamp(), aiMsg.message(), aiMsg.tokens(), aiMsg.toolCallInteraction))
              .withTotalTokenUsage(aiMsg.totalTokenUsage());
      case Event.Deleted __ ->
          currentState().clear();
    };
  }
}
