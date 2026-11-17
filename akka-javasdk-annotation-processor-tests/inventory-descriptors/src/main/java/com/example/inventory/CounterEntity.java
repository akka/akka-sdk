/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.inventory;

import akka.javasdk.annotations.Component;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;

@Component(id = ComponentIds.COUNTER)
public class CounterEntity extends EventSourcedEntity<Integer, CounterEntity.Increased> {

  public record Increased(int amount) {}

  @Override
  public Integer applyEvent(Increased event) {
    return currentState() + event.amount();
  }
}
