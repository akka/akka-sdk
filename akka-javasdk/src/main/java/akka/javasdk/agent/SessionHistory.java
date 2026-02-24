/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import java.util.List;

public record SessionHistory(
    List<SessionMessage> messages, long sequenceNumber, SessionMessage.TokenUsage tokenUsage) {

  public SessionHistory(List<SessionMessage> messages, long sequenceNumber) {
    this(messages, sequenceNumber, SessionMessage.TokenUsage.EMPTY);
  }

  public static final SessionHistory EMPTY =
      new SessionHistory(List.of(), 0, SessionMessage.TokenUsage.EMPTY);
}
