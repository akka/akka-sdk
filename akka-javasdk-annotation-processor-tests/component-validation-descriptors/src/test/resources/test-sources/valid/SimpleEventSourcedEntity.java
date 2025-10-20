package com.example;

import akka.javasdk.eventsourcedentity.EventSourcedEntity;

public class SimpleEventSourcedEntity extends EventSourcedEntity<Integer, SimpleEventSourcedEntity.CounterEvent> {

  public sealed interface CounterEvent permits IncrementCounter, DecrementCounter {}

  public record IncrementCounter(int value) implements CounterEvent {}

  public record DecrementCounter(int value) implements CounterEvent {}

  @Override
  public Integer applyEvent(CounterEvent event) {
    return 0;
  }
}
