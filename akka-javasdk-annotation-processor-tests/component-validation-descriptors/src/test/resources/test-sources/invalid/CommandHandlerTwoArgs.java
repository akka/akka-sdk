/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;

@Component(id = "command-handler-two-args-entity")
public class CommandHandlerTwoArgs extends EventSourcedEntity<String, CommandHandlerTwoArgs.Event> {

  public sealed interface Event {
    record Created(String name, int age) implements Event {}
  }

  // Command handler with more than one parameter - not allowed
  public Effect create(String name, int age) {
    return effects().persist(new Event.Created(name, age));
  }

  public Event.Created onEvent(Event.Created event) {
    return event;
  }
}