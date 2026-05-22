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
 * and the caller should stream the journal in {@code [fromSequenceNr, toSequenceNr]} to reconstruct
 * the full history.
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
   * journal for this session in {@code [fromSequenceNr, toSequenceNr]} (both inclusive) to
   * reconstruct the rest.
   *
   * <p>{@code fromSequenceNr} is the last compaction point: events before it have been superseded
   * by the compaction summary now sitting in the entity, so re-reading them would duplicate
   * content. When no compaction has happened yet, {@code fromSequenceNr} is {@code 0}.
   *
   * <p>{@code toSequenceNr} is the entity's sequence number at the time of the reply — i.e. the
   * high-water mark of the in-memory state the caller is reconstructing. Bounding the journal read
   * upward prevents events persisted after the reply (e.g. a concurrent compaction that lands
   * between the entity reply and the journal stream) from leaking into the reconstructed history.
   */
  record Truncated(long fromSequenceNr, long toSequenceNr) implements SessionHistoryResult {}
}
