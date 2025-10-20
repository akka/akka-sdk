/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;

@Component(id = "not-public-event-sourced-entity")
class NotPublicEventSourcedEntity extends EventSourcedEntity<String, NotPublicEventSourcedEntity.Event> {

  public sealed interface Event {
    record Created(String name) implements Event {}
  }

  public Effect create(String name) {
    return effects().persist(new Event.Created(name));
  }

  public Event.Created onEvent(Event.Created event) {
    return event;
  }
}