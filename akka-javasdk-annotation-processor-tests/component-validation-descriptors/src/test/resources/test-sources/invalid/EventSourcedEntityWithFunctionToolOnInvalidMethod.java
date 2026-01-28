/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;

@Component(id = "event-sourced-entity-with-function-tool-on-invalid-method")
public class EventSourcedEntityWithFunctionToolOnInvalidMethod extends EventSourcedEntity<String, EventSourcedEntityWithFunctionToolOnInvalidMethod.Event> {

  public sealed interface Event {
    record Created(String name) implements Event {}
  }

  public Effect<String> create(String name) {
    return effects().persist(new Event.Created(name)).thenReply(__ -> "created");
  }

  // @FunctionTool is not allowed on methods that don't return Effect or ReadOnlyEffect
  @FunctionTool(description = "This should not be allowed on non-Effect methods")
  public String invalidMethod() {
    return "invalid";
  }

  public Event.Created onEvent(Event.Created event) {
    return event;
  }
}
