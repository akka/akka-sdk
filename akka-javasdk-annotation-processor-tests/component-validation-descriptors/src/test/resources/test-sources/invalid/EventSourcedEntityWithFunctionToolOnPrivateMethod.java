/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;

@Component(id = "event-sourced-entity-with-function-tool-on-private-method")
public class EventSourcedEntityWithFunctionToolOnPrivateMethod extends EventSourcedEntity<String, EventSourcedEntityWithFunctionToolOnPrivateMethod.Event> {

  public sealed interface Event {
    record Created(String name) implements Event {}
  }

  public Effect<String> create(String name) {
    return effects().persist(new Event.Created(name)).thenReply(__ -> "created");
  }

  // @FunctionTool is not allowed on private methods
  @FunctionTool(description = "This should not be allowed on private methods")
  private Effect<String> privateMethod() {
    return effects().reply("private");
  }

  public Event.Created onEvent(Event.Created event) {
    return event;
  }
}
