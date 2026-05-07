/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import java.util.List;

public record SessionHistory(
    List<SessionMessage> messages,
    long sequenceNumber,
    SessionMessage.TokenUsage tokenUsage,
    boolean truncated,
    long compactionSeqNr) {

  public SessionHistory(List<SessionMessage> messages, long sequenceNumber) {
    this(messages, sequenceNumber, SessionMessage.TokenUsage.EMPTY, false, 0L);
  }

  public static final SessionHistory EMPTY =
      new SessionHistory(List.of(), 0, SessionMessage.TokenUsage.EMPTY, false, 0L);
}
