/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import java.util.List;

public record SessionHistory(List<SessionMessage> messages, long sequenceNumber) {
  public static final SessionHistory EMPTY = new SessionHistory(List.of(), 0);
}
