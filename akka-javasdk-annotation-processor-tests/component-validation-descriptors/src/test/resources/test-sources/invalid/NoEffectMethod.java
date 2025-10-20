/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;

@Component(id = "no-effect-method-entity")
public class NoEffectMethod extends EventSourcedEntity<String, NoEffectMethod.Event> {

  public sealed interface Event {
    record Created(String name) implements Event {}
  }

  // No Effect method - this should cause a validation error

  public Event.Created onEvent(Event.Created event) {
    return event;
  }
}