/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;

@ComponentId("simple-event-sourced")
public class SimpleEventSourcedEntity extends EventSourcedEntity<SimpleEventSourcedEntity.State, SimpleEventSourcedEntity.Event> {


  record State(String value) {}
  record Event(String value) {}


  @Override
  public State applyEvent(Event event) {
    return new State(event.value);
  }
}
