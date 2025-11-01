/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;

@Component(id = "valid-event-sourced-entity")
public class ValidEventSourcedEntity extends EventSourcedEntity<String, ValidEventSourcedEntity.Event> {

  // Sealed event type as required
  public sealed interface Event {
    record Created(String name) implements Event {}
    record Updated(String name) implements Event {}
  }

  public Effect create(String name) {
    return effects().persist(new Event.Created(name));
  }

  public Event.Created onEvent(Event.Created event) {
    return event;
  }

  public Event.Updated onEvent(Event.Updated event) {
    return event;
  }
}