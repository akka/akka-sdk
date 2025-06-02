/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import java.util.List;

public record SessionHistory(List<SessionMessage> messages) {
  public static final SessionHistory EMPTY = new SessionHistory(List.of());
}
