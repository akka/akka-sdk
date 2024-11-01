/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.eventsourcedentity.EventSourcedEntity;

public abstract class AbstractInbetweenEsEntity extends EventSourcedEntity<AbstractInbetweenEsEntity.State, AbstractInbetweenEsEntity.Event> {

  public record State(String value) {}
  public record Event(String value) {}

  @Override
  public State applyEvent(Event event) {
    return new State(event.value());
  }
}
