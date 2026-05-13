/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent;

import akka.annotation.InternalApi;
import akka.japi.pf.PFBuilder;
import akka.javasdk.agent.AgentRegistry;
import akka.javasdk.agent.MemoryFilter;
import akka.javasdk.agent.SessionHistory;
import akka.javasdk.agent.SessionHistoryResult;
import akka.javasdk.agent.SessionMemory;
import akka.javasdk.agent.SessionMemoryEntity;
import akka.javasdk.agent.SessionMessage;
import akka.javasdk.agent.SessionMessageConverter;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.impl.serialization.Serializer;
import akka.runtime.sdk.spi.EventLogClient;
import akka.stream.Materializer;
import akka.stream.javadsl.Sink;
import com.typesafe.config.Config;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.PartialFunction;

/** INTERNAL USE Not for user extension or instantiation */
@InternalApi
public final class SessionMemoryClient implements SessionMemory {

  public record MemorySettings(
      boolean read,
      boolean write,
      Optional<Integer> historyLimit,
      List<MemoryFilter> memoryFilters) {

    static MemorySettings disabled() {
      return new MemorySettings(false, false, Optional.empty(), List.of());
    }

    static MemorySettings enabled() {
      return new MemorySettings(true, true, Optional.empty(), List.of());
    }
  }

  private final Logger logger = LoggerFactory.getLogger(SessionMemoryClient.class);
  private final ComponentClient componentClient;
  private final EventLogClient eventLogClient;
  private final Serializer serializer;
  private final AgentRegistry agentRegistry;
  private final Materializer materializer;
  private final MemorySettings memorySettings;
  private final PartialFunction<Object, SessionMessage> sessionMessageCollectPF;

  public SessionMemoryClient(
      ComponentClient componentClient,
      EventLogClient eventLogClient,
      Serializer serializer,
      AgentRegistry agentRegistry,
      Materializer materializer,
      Config memoryConfig) {
    this(
        componentClient,
        eventLogClient,
        serializer,
        agentRegistry,
        materializer,
        memoryConfig.getBoolean("enabled") ? MemorySettings.enabled() : MemorySettings.disabled());
  }

  public SessionMemoryClient(
      ComponentClient componentClient,
      EventLogClient eventLogClient,
      Serializer serializer,
      AgentRegistry agentRegistry,
      Materializer materializer,
      MemorySettings memorySettings) {
    this.componentClient = componentClient;
    this.eventLogClient = eventLogClient;
    this.serializer = serializer;
    this.agentRegistry = agentRegistry;
    this.materializer = materializer;
    this.memorySettings = memorySettings;

    this.sessionMessageCollectPF =
        new PFBuilder<Object, SessionMessage>()
            .match(SessionMemoryEntity.Event.Message.class, SessionMessageConverter::apply)
            .build();
  }

  @Override
  public void addInteraction(
      String sessionId,
      SessionMessage.MultimodalUserMessage userMessage,
      List<SessionMessage> messages) {
    if (memorySettings.write()) {
      logger.debug("Adding interaction to sessionId [{}]", sessionId);
      componentClient
          .forEventSourcedEntity(sessionId)
          .method(SessionMemoryEntity::addMultimodalInteraction)
          .invoke(new SessionMemoryEntity.AddMultimodalInteractionCmd(userMessage, messages));
    } else {
      logger.debug(
          "Memory writing is disabled, interaction not added to sessionId [{}]", sessionId);
    }
  }

  @Override
  public void addInteraction(
      String sessionId, SessionMessage.UserMessage userMessage, List<SessionMessage> messages) {
    if (memorySettings.write()) {
      logger.debug("Adding interaction to sessionId [{}]", sessionId);
      componentClient
          .forEventSourcedEntity(sessionId)
          .method(SessionMemoryEntity::addInteraction)
          .invoke(new SessionMemoryEntity.AddInteractionCmd(userMessage, messages));
    } else {
      logger.debug(
          "Memory writing is disabled, interaction not added to sessionId [{}]", sessionId);
    }
  }

  /**
   * Returns the session history for the model.
   *
   * <p>Two sources are queried, with different trade-offs:
   *
   * <ol>
   *   <li>{@link SessionMemoryEntity#fetchHistory}. Hits an entity that may live on a different
   *       node and returns the whole history in a single response, so the payload is bounded by the
   *       cross-node message-size limit. The entity caps the history at {@code
   *       akka.javasdk.agent.memory.limited-window.max-size}: when the cap is reached it drops the
   *       oldest messages and signals via a {@link SessionHistoryResult.Truncated} reply. Cheap
   *       when the history fits, but cannot deliver more than the cap allows.
   *   <li>{@link EventLogClient#currentEventsForEntity(EventLogClient.Query)}. The runtime reads
   *       the journal locally (same node) and streams the events back chunked, so it is not bound
   *       by the cross-node message-size limit and never has to hold the full history in memory at
   *       once. More expensive than a single entity read, so we only pay for it when needed.
   * </ol>
   *
   * <p>Strategy: ask the entity first; if it replies {@code Truncated}, switch to the chunked
   * journal stream so the model never sees an incomplete context.
   */
  @Override
  public SessionHistory getHistory(String sessionId) {
    if (!memorySettings.read()) {
      logger.debug(
          "Memory reading is disabled, history not retrieved for sessionId [{}]", sessionId);
      return SessionHistory.EMPTY;
    }

    var result =
        componentClient
            .forEventSourcedEntity(sessionId)
            .method(SessionMemoryEntity::fetchHistory)
            .invoke(
                new SessionMemoryEntity.GetHistoryCmd(
                    memorySettings.historyLimit, memorySettings.memoryFilters));

    return switch (result) {
      case SessionHistoryResult.Loaded(var history) -> {
        logger.debug(
            "History retrieved from entity for sessionId [{}], size [{}]",
            sessionId,
            history.messages().size());
        yield history;
      }
      case SessionHistoryResult.Truncated(var fromSequenceNr) ->
          fetchHistoryFromJournal(sessionId, fromSequenceNr);
    };
  }

  /**
   * Fetch the journal for {@code sessionId} starting at {@code fromSequenceNr} and apply the same
   * filter + last-N logic the entity uses, so the caller sees an equivalent slice of history
   * regardless of which path produced it.
   *
   * <p>Package-private so the fallback can be exercised in isolation by tests without having to
   * stand up a full {@link ComponentClient}.
   */
  SessionHistory fetchHistoryFromJournal(String sessionId, long fromSequenceNr) {
    var query =
        new EventLogClient.Query(
            SessionMemoryEntity.SESSION_MEMORY_COMPONENT_ID, sessionId, fromSequenceNr);

    // The stream is materialized on Akka's dispatcher; join() parks the calling virtual thread,
    // unmounting it from its carrier until the CompletionStage completes.
    // Safe because callers (AgentImpl) invoke this from SdkExecutionContext (virtual threads).
    List<SessionMessage> messages =
        eventLogClient
            .currentEventsForEntity(query)
            .asJava()
            .map(envelope -> serializer.fromBytes(envelope.payload()))
            .collect(sessionMessageCollectPF)
            .runWith(Sink.seq(), materializer)
            .toCompletableFuture()
            .join();

    var filtered =
        MemoryHistoryUtils.applyFilters(
            messages, memorySettings.memoryFilters, MemoryHistoryUtils.roleLookup(agentRegistry));
    var trimmed = MemoryHistoryUtils.trimToLastN(filtered, memorySettings.historyLimit);

    logger.debug(
        "History retrieved from journal for sessionId [{}], size [{}]", sessionId, trimmed.size());

    // The journal-derived history isn't tied to a specific entity sequence number and we don't
    // accumulate token usage here; downstream callers (the agent runtime) only consume messages.
    return new SessionHistory(new ArrayList<>(trimmed), 0L, SessionMessage.TokenUsage.EMPTY);
  }
}
