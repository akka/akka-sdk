/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eventsourced;

import com.google.common.collect.Multimap;


public sealed interface CounterEvent {

  record Increased(String counterId, int value) implements CounterEvent {
  }

  record Set(String counterId, int value, Multimap<String, String> notSerializableField) implements CounterEvent {
  }
}
