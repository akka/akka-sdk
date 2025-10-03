/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import static akka.Done.done;

import akka.Done;
import akka.javasdk.agent.SessionMemoryEntity.Event;
import akka.javasdk.agent.SessionMemoryEntity.State;
import akka.javasdk.agent.SessionMessage.AiMessage;
import akka.javasdk.agent.SessionMessage.ToolCallResponse;
import akka.javasdk.agent.SessionMessage.UserMessage;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.TypeName;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import com.typesafe.config.Config;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Built-in Event Sourced Entity that provides persistent session memory for agent interactions with
 * the AI model.
 *
 * <p>SessionMemoryEntity maintains a limited history of contextual messages in FIFO (First In,
 * First Out) style, automatically managing memory size to prevent unbounded growth. It serves as
 * the default implementation of session memory for agents.
 *
 * <p><strong>Automatic Registration:</strong> This entity is automatically registered by the Akka
 * runtime when Agent components are detected. Each session is identified by the entity id, which
 * corresponds to the agent's session id.
 *
 * <p><strong>Memory Management:</strong>
 *
 * <ul>
 *   <li>Configurable maximum memory size via {@code
 *       akka.javasdk.agent.memory.limited-window.max-size}
 *   <li>Automatic removal of oldest messages when size limit is exceeded
 *   <li>Orphan message cleanup (removes AI/tool messages when their triggering user message is
 *       removed)
 * </ul>
 *
 * <p><strong>Direct Access:</strong> You can interact directly with session memory using {@link
 * ComponentClient}.
 *
 * <p><strong>Event Subscription:</strong> You can subscribe to session memory events using a {@link
 * akka.javasdk.consumer.Consumer} to monitor session activity, implement custom analytics, or
 * trigger compaction when memory usage exceeds thresholds.
 */
@Component(
    id = "akka-session-memory",
    name = "Agent Session Memory",
    description = """
      Stores the recent conversation history for each agent session, including user, AI, and tool messages. 
      Use this component to view or inspect the memory that the agents uses for context during interactions.
      """
)
public final class SessionMemoryEntity extends EventSourcedEntity<State, Event> {

  private static final Logger log = LoggerFactory.getLogger(SessionMemoryEntity.class);

  private final Config config;
  private final String sessionId;

  public SessionMemoryEntity(Config config, EventSourcedEntityContext context) {
    this.config = config;
    this.sessionId = context.entityId();
  }

  public record State(
      String sessionId,
      long maxSizeInBytes,
      long currentSizeInBytes,
      List<SessionMessage> messages) {

    private static final Logger logger = LoggerFactory.getLogger(State.class);

    public State {
      if (maxSizeInBytes <= 0)
        throw new IllegalArgumentException("Maximum size must be greater than 0");
      messages = messages != null ? messages : new LinkedList<>();
      currentSizeInBytes =
          enforceMaxCapacity(sessionId, messages, currentSizeInBytes, maxSizeInBytes);
    }

    public boolean isEmpty() {
      return messages.isEmpty();
    }

    public State withMaxSize(int newMaxSize) {
      return new State(sessionId, newMaxSize, currentSizeInBytes, messages);
    }

    public State addMessage(SessionMessage message) {
      // avoid copies of the list for efficiency, need to be careful not to return the list ref to
      // outside
      messages.add(message);

      var updatedSize = currentSizeInBytes + message.size();
      return new State(sessionId, maxSizeInBytes, updatedSize, messages);
    }

    public State clear() {
      return new State(sessionId, maxSizeInBytes, 0, new LinkedList<>());
    }

    private static long enforceMaxCapacity(
        String sessionId, List<SessionMessage> messages, long currentSize, long maxSize) {
      var freedSpace = 0;
      while ((currentSize - freedSpace) > maxSize) {
        freedSpace += messages.removeFirst().size();
        logger.debug(
            "Removed oldest message for sessionId [{}]. Remaining size [{}], maxSizeInBytes [{}]",
            sessionId,
            currentSize,
            maxSize);
      }

      // remove all messages that are not UserMessage since those were driven by the deleted
      // UserMessage
      while (!messages.isEmpty() && !(messages.getFirst() instanceof UserMessage)) {
        freedSpace += messages.removeFirst().size();
        logger.debug(
            "Removed orphan message for sessionId [{}]. Remaining size [{}], maxSizeInBytes [{}]",
            sessionId,
            currentSize,
            maxSize);
      }

      return currentSize - freedSpace;
    }
  }

  @Override
  public State emptyState() {
    var maxSizeInBytes = config.getBytes("akka.javasdk.agent.memory.limited-window.max-size");
    return new State(sessionId, maxSizeInBytes, 0, new LinkedList<>());
  }

  /** Sealed interface representing events that can occur in the SessionMemory entity. */
  public sealed interface Event {

    @TypeName("akka-memory-limited-window-set")
    record LimitedWindowSet(Instant timestamp, int maxSizeInBytes) implements Event {}

    @TypeName("akka-memory-user-message-added")
    record UserMessageAdded(Instant timestamp, String componentId, String message, int sizeInBytes)
        implements Event {}

    @TypeName("akka-memory-ai-message-added")
    record AiMessageAdded(
        Instant timestamp,
        String componentId,
        String message,
        int sizeInBytes,
        long historySizeInBytes,
        List<SessionMessage.ToolCallRequest> toolCallRequests)
        implements Event {

      AiMessageAdded withHistorySizeInBytes(long newSize) {
        return new AiMessageAdded(
            timestamp, componentId, message, sizeInBytes, newSize, toolCallRequests);
      }
    }

    @TypeName("akka-memory-tool-response-message-added")
    record ToolResponseMessageAdded(
        Instant timestamp,
        String componentId,
        String id,
        String name,
        String content,
        int sizeInBytes)
        implements Event {}

    @TypeName("akka-memory-cleared")
    record HistoryCleared() implements Event {}

    @TypeName("akka-memory-deleted")
    record Deleted(Instant timestamp) implements Event {}
  }

  // Request commands
  public record LimitedWindow(int maxSizeInBytes) {}

  public Effect<Done> setLimitedWindow(LimitedWindow limitedWindow) {
    if (limitedWindow.maxSizeInBytes <= 0) {
      return effects().error("Maximum size must be greater than 0");
    } else {
      return effects()
          .persist(new Event.LimitedWindowSet(Instant.now(), limitedWindow.maxSizeInBytes))
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
            .map(
                msg -> {
                  return (Event)
                      switch (msg) {
                        case AiMessage aiMessage ->
                            new Event.AiMessageAdded(
                                aiMessage.timestamp(),
                                aiMessage.componentId(),
                                aiMessage.text(),
                                aiMessage.size(),
                                0L, // filled in later
                                aiMessage.toolCallRequests());

                        case SessionMessage.ToolCallResponse toolCallResponse ->
                            new Event.ToolResponseMessageAdded(
                                toolCallResponse.timestamp(),
                                toolCallResponse.componentId(),
                                toolCallResponse.id(),
                                toolCallResponse.name(),
                                toolCallResponse.text(),
                                toolCallResponse.size());

                        default ->
                            throw new IllegalArgumentException("Unsupported message: " + msg);
                      };
                })
            .toList();

    var userMessageEvent =
        new Event.UserMessageAdded(
            cmd.userMessage.timestamp(),
            cmd.userMessage.componentId(),
            cmd.userMessage.text(),
            cmd.userMessage.size());

    List<Event> allEvents = new ArrayList<>();
    allEvents.add(userMessageEvent);
    allEvents.addAll(modelAndToolEvents);

    var allEventsWithSize = updateHistorySize(allEvents);

    return effects().persistAll(allEventsWithSize).thenReply(__ -> done());
  }

  public record GetHistoryCmd(Optional<Integer> lastNMessages) {}

  public ReadOnlyEffect<SessionHistory> getHistory(GetHistoryCmd cmd) {
    List<SessionMessage> messages = currentState().messages();
    if (cmd.lastNMessages != null
        && cmd.lastNMessages.isPresent()
        && messages.size() > cmd.lastNMessages.get()) {
      var lastN = messages.subList(messages.size() - cmd.lastNMessages.get(), messages.size());
      // make sure this returns a copy of the list and not the list itself
      return effects()
          .reply(new SessionHistory(new LinkedList<>(lastN), commandContext().sequenceNumber()));
    } else {
      // make sure this returns a copy of the list and not the list itself
      return effects()
          .reply(new SessionHistory(new LinkedList<>(messages), commandContext().sequenceNumber()));
    }
  }

  public record CompactionCmd(UserMessage userMessage, AiMessage aiMessage, long sequenceNumber) {}

  public Effect<Done> compactHistory(CompactionCmd cmd) {

    if (!cmd.userMessage.componentId().equals(cmd.aiMessage.componentId()))
      return effects().error("componentId in userMessage must be the same as in the aiMessage");
    var componentId = cmd.userMessage.componentId();

    var events = new ArrayList<Event>();
    events.add(new Event.HistoryCleared());
    events.add(
        new Event.UserMessageAdded(
            cmd.userMessage.timestamp(),
            componentId,
            cmd.userMessage.text(),
            cmd.userMessage.size()));
    events.add(
        new Event.AiMessageAdded(
            cmd.aiMessage.timestamp(),
            componentId,
            cmd.aiMessage.text(),
            cmd.aiMessage.size(),
            0L, // filled in later
            Collections.emptyList()));

    if (commandContext().sequenceNumber() > cmd.sequenceNumber
        && !currentState().messages.isEmpty()) {
      int diff = (int) (commandContext().sequenceNumber() - cmd.sequenceNumber);
      currentState()
          .messages
          .subList(currentState().messages.size() - diff, currentState().messages.size())
          .forEach(
              msg -> {
                switch (msg) {
                  case UserMessage userMessage -> {
                    events.add(
                        new Event.UserMessageAdded(
                            userMessage.timestamp(),
                            userMessage.componentId(),
                            userMessage.text(),
                            userMessage.size()));
                  }

                  case ToolCallResponse toolCallResponse -> {
                    events.add(
                        new Event.ToolResponseMessageAdded(
                            toolCallResponse.timestamp(),
                            toolCallResponse.componentId(),
                            toolCallResponse.id(),
                            toolCallResponse.name(),
                            toolCallResponse.text(),
                            toolCallResponse.size()));
                  }

                  case AiMessage aiMessage -> {
                    events.add(
                        new Event.AiMessageAdded(
                            aiMessage.timestamp(),
                            aiMessage.componentId(),
                            aiMessage.text(),
                            aiMessage.size(),
                            0L, // filled in later
                            aiMessage.toolCallRequests()));
                  }
                }
              });
    }

    var eventsWithSize = updateHistorySize(events);

    return effects().persistAll(eventsWithSize).thenReply(__ -> Done.done());
  }

  private List<Event> updateHistorySize(List<Event> events) {
    var result = new ArrayList<Event>();
    var size = currentState().currentSizeInBytes;
    for (Event event : events) {
      switch (event) {
        case Event.HistoryCleared evt -> {
          size = 0L;
          result.add(evt);
        }
        case Event.UserMessageAdded evt -> {
          size += evt.sizeInBytes();
          result.add(evt);
        }
        case Event.ToolResponseMessageAdded evt -> {
          size += evt.sizeInBytes();
          result.add(evt);
        }
        case Event.AiMessageAdded evt -> {
          size += evt.sizeInBytes();
          // fill in the accumulated size in each AiMessageAdded
          result.add(evt.withHistorySizeInBytes(size));
        }
        case Event evt -> result.add(evt);
      }
    }

    return result;
  }

  public Effect<Done> delete() {
    if (isDeleted()) {
      return effects().reply(done());
    } else {
      return effects()
          .persist(new Event.Deleted(Instant.now()))
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
              .addMessage(
                  new UserMessage(userMsg.timestamp(), userMsg.message(), userMsg.componentId));

      case Event.AiMessageAdded aiMsg ->
          currentState()
              .addMessage(
                  new AiMessage(
                      aiMsg.timestamp, aiMsg.message, aiMsg.componentId, aiMsg.toolCallRequests));

      case Event.ToolResponseMessageAdded toolMsg ->
          currentState()
              .addMessage(
                  new ToolCallResponse(
                      toolMsg.timestamp(),
                      toolMsg.componentId,
                      toolMsg.id(),
                      toolMsg.name,
                      toolMsg.content()));

      case Event.HistoryCleared __ -> currentState().clear();

      case Event.Deleted __ -> currentState().clear();
    };
  }
}
