/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.eventsourcedentities.counter;

import akka.javasdk.annotations.TypeName;

public sealed interface CounterEvent {

  @TypeName("increased")
  record ValueIncreased(int value) implements CounterEvent {
  }

  @TypeName("set")
  record ValueSet(int value) implements CounterEvent {
  }

  @TypeName("multiplied")
  record ValueMultiplied(int value) implements CounterEvent {
  }
}
