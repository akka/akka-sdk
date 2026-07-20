/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import java.util.List;

/**
 * The full message history of a session, as loaded from {@link SessionMemoryEntity}.
 *
 * @param messages the messages in the session, in order
 * @param sequenceNumber the entity's sequence number at the time this history was loaded
 * @param tokenUsage total token usage across all {@link SessionMessage.AiMessage}s in the history
 */
public record SessionHistory(
    List<SessionMessage> messages, long sequenceNumber, SessionMessage.TokenUsage tokenUsage) {

  public SessionHistory(List<SessionMessage> messages, long sequenceNumber) {
    this(messages, sequenceNumber, SessionMessage.TokenUsage.EMPTY);
  }

  /** An empty history, as returned for a session that has never been written to. */
  public static final SessionHistory EMPTY =
      new SessionHistory(List.of(), 0, SessionMessage.TokenUsage.EMPTY);
}
