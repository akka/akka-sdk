/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eventsourced;

public sealed interface PolyState {

  record StateA() implements PolyState {
  }

  record StateB() implements PolyState {
  }
}
