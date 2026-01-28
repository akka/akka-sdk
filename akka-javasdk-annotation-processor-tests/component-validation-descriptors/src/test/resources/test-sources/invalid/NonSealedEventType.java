/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;

@Component(id = "non-sealed-event-entity")
public class NonSealedEventType extends EventSourcedEntity<String, NonSealedEventType.Event> {

  // Event type is NOT sealed - this should cause a validation error
  public interface Event {
    record Created(String name) implements Event {}
  }

  public Effect<String> create(String name) {
    return effects().persist(new Event.Created(name)).thenReply(__ -> "created");
  }

  public Event.Created onEvent(Event.Created event) {
    return event;
  }
}