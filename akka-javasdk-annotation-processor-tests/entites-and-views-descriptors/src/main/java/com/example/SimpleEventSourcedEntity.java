/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;

@Component(id = "simple-event-sourced")
public class SimpleEventSourcedEntity
    extends EventSourcedEntity<SimpleEventSourcedEntity.State, SimpleEventSourcedEntity.Event> {

  record State(String value) {}

  record Event(String value) {}

  @Override
  public State applyEvent(Event event) {
    return new State(event.value);
  }
}
