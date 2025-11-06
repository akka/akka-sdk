/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;

@Component(id = "duplicate-command-handlers-entity")
public class DuplicateCommandHandlers extends EventSourcedEntity<String, DuplicateCommandHandlers.Event> {

  public sealed interface Event {
    record Created(String name) implements Event {}
  }

  // Duplicate command handler names (overloaded) - not allowed
  public Effect create(String name) {
    return effects().persist(new Event.Created(name));
  }

  public Effect create() {
    return effects().persist(new Event.Created("default"));
  }

  public Event.Created onEvent(Event.Created event) {
    return event;
  }
}