/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import static akka.Done.done;

import akka.Done;
import akka.javasdk.agent.SessionMemoryEntity.Event;
import akka.javasdk.agent.SessionMemoryEntity.State;
import akka.javasdk.agent.SessionMessage.AiMessage;
import akka.javasdk.agent.SessionMessage.MultimodalUserMessage;
import akka.javasdk.agent.SessionMessage.TokenUsage;
import akka.javasdk.agent.SessionMessage.ToolCallResponse;
import akka.javasdk.agent.SessionMessage.UserMessage;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.EnableReplicationFilter;
import akka.javasdk.annotations.TypeName;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import akka.javasdk.eventsourcedentity.ReplicationFilter;
import akka.javasdk.impl.agent.MemoryHistoryUtils;
import com.typesafe.config.Config;
import java.time.Instant;
import java.util.*;
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
 *
 * <p>The session memory has the multi-region replication filter enabled to only include the local
 * region when using `request-region` primary selection. When accessed from another region the
 * filter will be expanded to include the other region too.
 */
@Component(
    id = SessionMemoryEntity.COMPONENT_ID,
    name = "Agent Session Memory",
    description =
"""
Stores the recent conversation history for each agent session, including user, AI, and tool messages.
Use this component to view or inspect the memory that the agents uses for context during interactions.
""")
@EnableReplicationFilter
public final class SessionMemoryEntity extends EventSourcedEntity<State, Event> {

  public static final String COMPONENT_ID = "akka-session-memory";

  private final Config config;
  private final String sessionId;
  public final AgentRegistry agentRegistry;
  private final ReplicationFilter.Builder selfRegionFilter;

  public SessionMemoryEntity(
      Config config, EventSourcedEntityContext context, AgentRegistry agentRegistry) {
    this.config = config;
    this.sessionId = context.entityId();
    this.agentRegistry = agentRegistry;
    this.selfRegionFilter = ReplicationFilter.includeRegion(context.selfRegion());
  }

  public record State(
      String sessionId,
      long maxSizeInBytes,
      long currentSizeInBytes,
      List<SessionMessage> messages,
      TokenUsage tokenUsage,
      boolean truncated,
      long compactionSeqNr) {

    public State(
        String sessionId,
        long maxSizeInBytes,
        long currentSizeInBytes,
        List<SessionMessage> messages) {
      this(sessionId, maxSizeInBytes, currentSizeInBytes, messages, TokenUsage.EMPTY, false, 0L);
    }

    private static final Logger logger = LoggerFactory.getLogger(State.class);

    public State {
      if (maxSizeInBytes <= 0)
        throw new IllegalArgumentException("Maximum size must be greater than 0");
      messages = messages != null ? messages : new LinkedList<>();
      int sizeBefore = messages.size();
      currentSizeInBytes =
          enforceMaxCapacity(sessionId, messages, currentSizeInBytes, maxSizeInBytes);
      truncated = truncated || messages.size() < sizeBefore;
    }

    public boolean isEmpty() {
      return messages.isEmpty();
    }

    public State withMaxSize(int newMaxSize) {
      return new State(
          sessionId,
          newMaxSize,
          currentSizeInBytes,
          messages,
          tokenUsage,
          truncated,
          compactionSeqNr);
    }

    public State addMessage(SessionMessage message) {
      // avoid copies of the list for efficiency, need to be careful not to return the list ref to
      // outside
      messages.add(message);

      var updatedSize = currentSizeInBytes + message.size();

      var tokenUsage = this.tokenUsage;
      if (message instanceof AiMessage aiMessage) {
        tokenUsage = tokenUsage.add(aiMessage.tokenUsage());
      }

      return new State(
          sessionId, maxSizeInBytes, updatedSize, messages, tokenUsage, truncated, compactionSeqNr);
    }

    /**
     * Reset the in-memory history on deletion.
     *
     * <p>On entity deletion, the entity is kept around for some time before being purged, can still
     * serve reads, and rejects any further persists. After this reset {@code getHistory} returns an
     * empty session, so the agent sees no context and never falls back to a chunked journal read.
     * We therefore have nothing to anchor with a journal sequence number here: the {@code
     * compactionSeqNr} carried by any prior compaction is no longer relevant on a deleted entity,
     * and is reset to {@code 0} along with the rest of the state.
     */
    public State clear() {
      return new State(sessionId, maxSizeInBytes, 0, new LinkedList<>(), tokenUsage, false, 0L);
    }

    /**
     * Reset the in-memory history but record the journal sequence number where compaction took
     * place, so a subsequent chunked read from the journal can skip the events that were superseded
     * by the compaction summary.
     *
     * <p>Clears the {@code truncated} flag: the compaction summary plus a chunked read from {@code
     * compactedAtSeqNr} can rebuild the full history losslessly, so the entity is no longer in a
     * lossy state from the caller's perspective. If the post-compaction history later overflows
     * again, {@link State#State} will set {@code truncated} back to {@code true}.
     */
    public State compact(long compactedAtSeqNr) {
      return new State(
          sessionId, maxSizeInBytes, 0, new LinkedList<>(), tokenUsage, false, compactedAtSeqNr);
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

      // remove all messages that are not UserMessage or MultimodalUserMessage since those were
      // driven by the deleted UserMessage
      while (!messages.isEmpty()
          && !(messages.getFirst() instanceof UserMessage
              || messages.getFirst() instanceof MultimodalUserMessage)) {
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

    @TypeName("akka-memory-cleared")
    record HistoryCleared() implements Event {}

    @TypeName("akka-memory-deleted")
    record Deleted(Instant timestamp) implements Event {}

    /* marker interface to distinguish message events as opposed to lifecycle events */
    sealed interface Message {}

    @TypeName("akka-memory-user-message-added")
    record UserMessageAdded(Instant timestamp, String componentId, String message, int sizeInBytes)
        implements Event, Message {}

    @TypeName("akka-memory-multimodal-user-message-added")
    record MultimodalUserMessageAdded(
        Instant timestamp,
        String componentId,
        List<SessionMessage.MessageContent> contents,
        int sizeInBytes)
        implements Event, Message {}

    @TypeName("akka-memory-ai-message-added")
    record AiMessageAdded(
        Instant timestamp,
        String componentId,
        String message,
        int sizeInBytes,
        long historySizeInBytes,
        List<SessionMessage.ToolCallRequest> toolCallRequests,
        Optional<String> thinking,
        Optional<TokenUsage> tokenUsage,
        Map<String, Object> attributes)
        implements Event, Message {

      AiMessageAdded withHistorySizeInBytes(long newSize) {
        return new AiMessageAdded(
            timestamp,
            componentId,
            message,
            sizeInBytes,
            newSize,
            toolCallRequests,
            thinking,
            tokenUsage,
            attributes);
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
        implements Event, Message {}
  }

  // Request commands
  public record LimitedWindow(int maxSizeInBytes) {}

  public Effect<Done> setLimitedWindow(LimitedWindow limitedWindow) {
    if (limitedWindow.maxSizeInBytes <= 0) {
      return effects().error("Maximum size must be greater than 0");
    } else {
      return effects()
          .persist(new Event.LimitedWindowSet(Instant.now(), limitedWindow.maxSizeInBytes))
          .updateReplicationFilter(selfRegionFilter)
          .thenReply(__ -> done());
    }
  }

  public record AddInteractionCmd(UserMessage userMessage, List<SessionMessage> messages) {
    public AddInteractionCmd(UserMessage userMessage, AiMessage aiMessage) {
      this(userMessage, List.of(aiMessage));
    }
  }

  public record AddMultimodalInteractionCmd(
      MultimodalUserMessage userMessage, List<SessionMessage> messages) {}

  public Effect<Done> addMultimodalInteraction(AddMultimodalInteractionCmd cmd) {
    var userMessageEvent =
        new Event.MultimodalUserMessageAdded(
            cmd.userMessage.timestamp(),
            cmd.userMessage.componentId(),
            cmd.userMessage.contents(),
            cmd.userMessage.size());
    return addInteraction(cmd.messages, cmd.userMessage.componentId(), userMessageEvent);
  }

  public Effect<Done> addInteraction(AddInteractionCmd cmd) {
    // userMessage may be null for partial interactions (e.g. tool call responses completing
    // a previous AI message's tool calls, where there is no triggering user message)
    if (cmd.userMessage == null) {
      return addPartialInteraction(cmd.messages);
    } else {
      var userMessageEvent =
          new Event.UserMessageAdded(
              cmd.userMessage.timestamp(),
              cmd.userMessage.componentId(),
              cmd.userMessage.text(),
              cmd.userMessage.size());
      return addInteraction(cmd.messages, cmd.userMessage.componentId(), userMessageEvent);
    }
  }

  private Effect<Done> addPartialInteraction(List<SessionMessage> messages) {
    var events = createEvents(messages);
    var eventsWithSize = updateHistorySize(events);
    return effects()
        .persistAll(eventsWithSize)
        .updateReplicationFilter(selfRegionFilter)
        .thenReply(__ -> done());
  }

  private Effect<Done> addInteraction(
      List<SessionMessage> messages, String componentId, Event userMessageEvent) {
    if (messages.stream()
        .filter(msg -> msg instanceof AiMessage)
        .map(SessionMessage::componentId)
        .anyMatch(aiComponentId -> !componentId.equals(aiComponentId))) {
      return effects().error("componentId in userMessage must be the same as in all aiMessages");
    }

    List<Event> allEvents = new ArrayList<>();
    allEvents.add(userMessageEvent);
    allEvents.addAll(createEvents(messages));

    var allEventsWithSize = updateHistorySize(allEvents);

    return effects()
        .persistAll(allEventsWithSize)
        .updateReplicationFilter(selfRegionFilter)
        .thenReply(__ -> done());
  }

  private List<Event> createEvents(List<SessionMessage> messages) {
    return messages.stream()
        .map(
            msg ->
                (Event)
                    switch (msg) {
                      case AiMessage aiMessage ->
                          new Event.AiMessageAdded(
                              aiMessage.timestamp(),
                              aiMessage.componentId(),
                              aiMessage.text(),
                              aiMessage.size(),
                              0L, // filled in later
                              aiMessage.toolCallRequests(),
                              aiMessage.thinking(),
                              Optional.of(aiMessage.tokenUsage()),
                              aiMessage.attributes());

                      case ToolCallResponse toolCallResponse ->
                          new Event.ToolResponseMessageAdded(
                              toolCallResponse.timestamp(),
                              toolCallResponse.componentId(),
                              toolCallResponse.id(),
                              toolCallResponse.name(),
                              toolCallResponse.text(),
                              toolCallResponse.size());

                      default -> throw new IllegalArgumentException("Unsupported message: " + msg);
                    })
        .toList();
  }

  public record GetHistoryCmd(Optional<Integer> lastNMessages, List<MemoryFilter> memoryFilters) {

    public GetHistoryCmd() {
      this(Optional.empty(), List.of());
    }

    public GetHistoryCmd(Optional<Integer> lastNMessages) {
      this(lastNMessages, List.of());
    }

    public GetHistoryCmd(List<MemoryFilter> memoryFilters) {
      this(Optional.empty(), memoryFilters);
    }
  }

  private List<SessionMessage> filteredMessages(List<MemoryFilter> memoryFilters) {
    return MemoryHistoryUtils.applyFilters(
        currentState().messages(), memoryFilters, MemoryHistoryUtils.roleLookup(agentRegistry));
  }

  /**
   * Consistent read (not ReadOnlyEffect). Replication filter for the local region is enabled by
   * default and when accessing from another region it's important to trigger a region event sync
   * also for reads.
   */
  public Effect<TokenUsage> getTokenUsage() {
    return effects().reply(currentState().tokenUsage);
  }

  /**
   * Consistent read (not ReadOnlyEffect). Replication filter for the local region is enabled by
   * default and when accessing from another region it's important to trigger a region event sync
   * also for reads.
   */
  public Effect<SessionHistory> getHistory(GetHistoryCmd cmd) {
    return effects().reply(buildSessionHistory(cmd));
  }

  /* In case cmd.lastNMessages is null, we want to move on with an Optional.empty and not keep checking this. */
  private GetHistoryCmd sanitizeCmd(GetHistoryCmd cmd) {
    if (cmd.lastNMessages == null) {
      return new GetHistoryCmd(cmd.memoryFilters);
    } else {
      return cmd;
    }
  }

  /**
   * Like {@link #getHistory} but signals via a {@link SessionHistoryResult.Truncated} reply when
   * the entity has dropped older messages because of its size limit. Callers that receive a {@code
   * Truncated} marker should stream the journal in {@code [fromSequenceNr, toSequenceNr]} to
   * reconstruct the full history.
   *
   * <p>Internal SDK use: {@link akka.javasdk.impl.agent.SessionMemoryClient} calls this and, on a
   * {@code Truncated} reply, transparently falls back to a chunked journal read bounded by the
   * sequence numbers in the reply so the caller never sees an incomplete history.
   *
   * @see SessionHistoryResult.Truncated for the meaning of the returned sequence numbers.
   */
  public Effect<SessionHistoryResult> fetchHistory(GetHistoryCmd cmd) {
    var sanitizedCmd = sanitizeCmd(cmd);
    if (!currentState().truncated) {
      return effects().reply(new SessionHistoryResult.Loaded(buildSessionHistory(cmd)));
    }

    // Truncated: we can still satisfy a "last N" request when the in-memory state (after
    // filtering) already contains at least N matches, because truncation only drops the oldest
    // messages — anything newer is guaranteed to still be in memory.
    if (sanitizedCmd.lastNMessages.isPresent()) {
      var results = buildSessionHistory(sanitizedCmd);
      if (results.messages().size() >= sanitizedCmd.lastNMessages.get()) {
        return effects().reply(new SessionHistoryResult.Loaded(results));
      }
    }

    return effects()
        .reply(
            new SessionHistoryResult.Truncated(
                currentState().compactionSeqNr, commandContext().sequenceNumber()));
  }

  private SessionHistory buildSessionHistory(GetHistoryCmd cmd) {
    var filtered = filteredMessages(cmd.memoryFilters);
    var trimmed = MemoryHistoryUtils.trimToLastN(filtered, cmd.lastNMessages);
    // make sure this returns a copy of the list and not the list itself
    return new SessionHistory(
        new LinkedList<>(trimmed), commandContext().sequenceNumber(), currentState().tokenUsage);
  }

  // keeping UserMessage instead of MultimodalUserMessage for compaction
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
            Collections.emptyList(),
            cmd.aiMessage.thinking(),
            Optional.of(cmd.aiMessage.tokenUsage()),
            cmd.aiMessage.attributes()));

    if (commandContext().sequenceNumber() > cmd.sequenceNumber
        && !currentState().messages.isEmpty()) {
      int diff = (int) (commandContext().sequenceNumber() - cmd.sequenceNumber);
      currentState()
          .messages
          .subList(currentState().messages.size() - diff, currentState().messages.size())
          .forEach(
              msg -> {
                switch (msg) {
                  case UserMessage userMessage ->
                      events.add(
                          new Event.UserMessageAdded(
                              userMessage.timestamp(),
                              userMessage.componentId(),
                              userMessage.text(),
                              userMessage.size()));

                  case ToolCallResponse toolCallResponse ->
                      events.add(
                          new Event.ToolResponseMessageAdded(
                              toolCallResponse.timestamp(),
                              toolCallResponse.componentId(),
                              toolCallResponse.id(),
                              toolCallResponse.name(),
                              toolCallResponse.text(),
                              toolCallResponse.size()));

                  case AiMessage aiMessage ->
                      events.add(
                          new Event.AiMessageAdded(
                              aiMessage.timestamp(),
                              aiMessage.componentId(),
                              aiMessage.text(),
                              aiMessage.size(),
                              0L, // filled in later
                              aiMessage.toolCallRequests(),
                              aiMessage.thinking(),
                              Optional.empty(),
                              aiMessage.attributes()));

                  case MultimodalUserMessage multimodalUserMessage ->
                      events.add(
                          new Event.MultimodalUserMessageAdded(
                              multimodalUserMessage.timestamp(),
                              multimodalUserMessage.componentId(),
                              multimodalUserMessage.contents(),
                              multimodalUserMessage.size()));
                }
              });
    }

    var eventsWithSize = updateHistorySize(events);

    return effects()
        .persistAll(eventsWithSize)
        .updateReplicationFilter(selfRegionFilter)
        .thenReply(__ -> done());
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
        case Event.MultimodalUserMessageAdded evt -> {
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
          .updateReplicationFilter(selfRegionFilter)
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
          currentState().addMessage(SessionMessageConverter.apply(userMsg));

      case Event.MultimodalUserMessageAdded multimodalUserMsg ->
          currentState().addMessage(SessionMessageConverter.apply(multimodalUserMsg));

      case Event.AiMessageAdded aiMsg ->
          currentState().addMessage(SessionMessageConverter.apply(aiMsg));

      case Event.ToolResponseMessageAdded toolMsg ->
          currentState().addMessage(SessionMessageConverter.apply(toolMsg));

      case Event.HistoryCleared __ -> currentState().compact(eventContext().sequenceNumber());

      case Event.Deleted __ -> currentState().clear();
    };
  }
}
