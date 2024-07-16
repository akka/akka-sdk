/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.eventsourcedentities.headers;

import com.example.wiring.actions.echo.Message;
import akka.platform.javasdk.annotations.ForwardHeaders;
import akka.platform.javasdk.annotations.ComponentId;
import akka.platform.javasdk.eventsourcedentity.EventSourcedEntity;

import static com.example.wiring.actions.headers.ForwardHeadersAction.SOME_HEADER;

@ComponentId("forward-headers-es")
@ForwardHeaders(SOME_HEADER)
public class ForwardHeadersESEntity extends EventSourcedEntity<String, ForwardHeadersESEntity.Event> {

  sealed interface Event {
    public record Created() implements Event {}
  }

  public Effect<Message> createUser() {
    String headerValue = commandContext().metadata().get(SOME_HEADER).orElse("");
    return effects().reply(new Message(headerValue));
  }

  @Override
  public String applyEvent(Event event) {
    return currentState();
  }
}
