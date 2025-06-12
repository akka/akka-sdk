/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import akka.Done;
import akka.javasdk.agent.SessionMemoryEntity.Event;
import akka.javasdk.agent.SessionMemoryEntity.State;
import akka.javasdk.agent.SessionMessage.AiMessage;
import akka.javasdk.agent.SessionMessage.ToolCallResponse;
import akka.javasdk.agent.SessionMessage.UserMessage;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.TypeName;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
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
 * {@link ComponentClient} can be used to interact directly with this entity.
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

    public State withTotalTokenUsage(long tokenUsage) {
      return new State(maxLengthInBytes, currentLengthInBytes, messages, tokenUsage);
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
    record UserMessageAdded(long timestamp, String componentId, String message) implements Event {
    }

    @TypeName("akka-memory-ai-message-added")
    record AiMessageAdded(long timestamp,
                          String componentId,
                          String message,
                          int inputTokens,
                          int outputTokens,
                          List<SessionMessage.ToolCallRequest> toolCallRequests) implements Event {

    }

    @TypeName("akka-memory-tool-response-message-added")
    record ToolResponseMessageAdded(long timestamp,
                                    String componentId,
                                    String id,
                                    String name,
                                    String content) implements Event {

    }

    @TypeName("akka-memory-cleared")
    record HistoryCleared() implements Event {
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

  public record AddInteractionCmd(UserMessage userMessage, List<SessionMessage> messages) {
    public AddInteractionCmd(UserMessage userMessage, AiMessage aiMessage) {
      this(userMessage, List.of(aiMessage));
    }
  }

  public Effect<Done> addInteraction(AddInteractionCmd cmd) {

    if (cmd.messages.stream()
      .filter(msg -> msg instanceof AiMessage)
      .map(msg -> ((AiMessage) msg).componentId())
      .anyMatch(aiComponentId -> !cmd.userMessage.componentId().equals(aiComponentId))) {
      return effects().error("componentId in userMessage must be the same as in all aiMessages");
    }

    var modelAndToolEvents =
      cmd.messages.stream()
        .map(msg -> {

            return (Event) switch (msg) {
              case AiMessage(
                long timestamp, String text, String componentId, int inputTokens, int outputTokens,
                List<SessionMessage.ToolCallRequest> toolCallRequests
              ) -> new Event.AiMessageAdded(timestamp, componentId, text,
                inputTokens,
                outputTokens,
                toolCallRequests);

              case SessionMessage.ToolCallResponse(
                long timestamp, String componentId, String id, String name, String content
              ) -> new Event.ToolResponseMessageAdded(
                timestamp,
                componentId,
                id, name, content);

              default -> throw new IllegalArgumentException("Unsupported message: " + msg);
            };
          }
        ).toList();

    var userMessageEvent = new Event.UserMessageAdded(
      cmd.userMessage.timestamp(),
      cmd.userMessage.componentId(),
      cmd.userMessage.text());

    List<Event> allEvents = new ArrayList<>();
    allEvents.add(userMessageEvent);
    allEvents.addAll(modelAndToolEvents);

    return effects()
        .persistAll(allEvents)
        .thenReply(__ -> done());
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
        new SessionHistory(new LinkedList<>(lastN), commandContext().sequenceNumber()));
    } else {
      // make sure this returns a copy of the list and not the list itself
      return effects().reply(
        new SessionHistory(new LinkedList<>(messages), commandContext().sequenceNumber()));
    }
  }

  public record CompactionCmd(UserMessage userMessage, AiMessage aiMessage, long sequenceNumber) {
  }

  public Effect<Done> compactHistory(CompactionCmd cmd) {

    if (!cmd.userMessage.componentId().equals(cmd.aiMessage.componentId()))
      return effects().error("componentId in userMessage must be the same as in the aiMessage");
    var componentId = cmd.userMessage.componentId();

    var events = new ArrayList<Event>();
    events.add(new Event.HistoryCleared());
    events.add(new Event.UserMessageAdded(cmd.userMessage.timestamp(), componentId, cmd.userMessage.text()));
    events.add(new Event.AiMessageAdded(
      cmd.aiMessage.timestamp(),
      componentId,
      cmd.aiMessage.text(),
      cmd.aiMessage.inputTokens(),
      cmd.aiMessage.outputTokens(),
      Collections.emptyList()));

    if (commandContext().sequenceNumber() > cmd.sequenceNumber && !currentState().messages.isEmpty()) {
      int diff = (int) (commandContext().sequenceNumber() - cmd.sequenceNumber);
      currentState().messages.subList(currentState().messages.size() - diff, currentState().messages.size())
          .forEach(msg -> {
        switch (msg) {
          case UserMessage userMessage -> {
            events.add(new Event.UserMessageAdded(userMessage.timestamp(), userMessage.componentId(), userMessage.text()));
          }

          case ToolCallResponse toolCallResponse -> {
            events.add(new Event.ToolResponseMessageAdded(
              toolCallResponse.timestamp(),
              toolCallResponse.componentId(),
              toolCallResponse.id(),
              toolCallResponse.name(),
              toolCallResponse.text()));
          }

          case AiMessage aiMessage -> {
            events.add(new Event.AiMessageAdded(
              aiMessage.timestamp(),
              aiMessage.componentId(),
              aiMessage.text(),
              aiMessage.inputTokens(),
              aiMessage.outputTokens(),
              aiMessage.toolCallRequests()));
          }
        }
      });
    }

    return effects()
        .persistAll(events)
        .thenReply(__ -> Done.done());
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
            .addMessage(new UserMessage(userMsg.timestamp(), userMsg.message(), userMsg.componentId));

      case Event.AiMessageAdded aiMsg ->
          currentState()
            .addMessage(
              new AiMessage(
                aiMsg.timestamp(),
                aiMsg.message(),
                aiMsg.componentId,
                aiMsg.inputTokens(),
                aiMsg.outputTokens,
                aiMsg.toolCallRequests))
            .withTotalTokenUsage(aiMsg.inputTokens + aiMsg.outputTokens);

      case Event.ToolResponseMessageAdded toolMsg ->
          currentState()
            .addMessage(
              new ToolCallResponse(
                toolMsg.timestamp(),
                toolMsg.componentId,
                toolMsg.id(),
                toolMsg.name,
                toolMsg.content()));

      case Event.HistoryCleared __ ->
        currentState().clear();

      case Event.Deleted __ ->
          currentState().clear();
    };
  }
}
