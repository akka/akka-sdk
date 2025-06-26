/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eventsourced;

/**
 * Not serializable Response, missing Jackson annotations.
 */
public sealed interface Response {
  record OK() implements Response {
  }
  record Error() implements Response {
  }
}
