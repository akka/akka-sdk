/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;

@Component(id = "valid-event-sourced-entity-with-function-tool")
public class ValidEventSourcedEntityWithFunctionTool extends EventSourcedEntity<String, ValidEventSourcedEntityWithFunctionTool.Event> {

  public sealed interface Event {
    record Created(String name) implements Event {}
  }

  @FunctionTool(description = "This is allowed on Effect")
  public Effect<String> create(String name) {
    return effects().persist(new Event.Created(name)).thenReply(__ -> "created");
  }

  @FunctionTool(description = "This is allowed on ReadOnlyEffect")
  public ReadOnlyEffect<String> query() {
    return effects().reply("query result");
  }

  public Event.Created onEvent(Event.Created event) {
    return event;
  }

  @Override
  public String applyEvent(com.example.ValidEventSourcedEntityWithFunctionTool.Event event) {
    return "";
  }
}
