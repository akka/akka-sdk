/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Outcome of {@link SessionMemoryEntity#fetchHistory}.
 *
 * <p>Either {@link Loaded}, carrying the {@link SessionHistory} the caller can use directly, or
 * {@link Truncated}, signalling that the entity dropped older messages because of its size limit
 * and the caller should stream the journal from {@code fromSequenceNr} to reconstruct the full
 * history.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "@type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = SessionHistoryResult.Loaded.class, name = "L"),
  @JsonSubTypes.Type(value = SessionHistoryResult.Truncated.class, name = "T")
})
public sealed interface SessionHistoryResult {

  /** The entity returned the full history within its size limit. */
  record Loaded(SessionHistory history) implements SessionHistoryResult {}

  /**
   * The entity could not deliver the full history within its in-memory size limit. Stream the
   * journal for this session starting at {@code fromSequenceNr} (inclusive) to read the rest.
   *
   * <p>{@code fromSequenceNr} is the last compaction point: events before it have been superseded
   * by the compaction summary now sitting in the entity, so re-reading them would duplicate
   * content. When no compaction has happened yet, {@code fromSequenceNr} is {@code 0}.
   */
  record Truncated(long fromSequenceNr) implements SessionHistoryResult {}
}
