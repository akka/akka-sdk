/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.eventsourcedentities.headers;

import com.example.wiring.actions.echo.Message;
import akka.javasdk.annotations.ForwardHeaders;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;

import static com.example.wiring.eventsourcedentities.headers.ForwardHeadersESEntity.SOME_HEADER;

@ComponentId("forward-headers-es")
@ForwardHeaders(SOME_HEADER)
public class ForwardHeadersESEntity extends EventSourcedEntity<String, ForwardHeadersESEntity.Event> {

  public static final String SOME_HEADER = "some-header";

  sealed interface Event {
    public record Created() implements Event {}
  }

  public ReadOnlyEffect<Message> createUser() {
    String headerValue = commandContext().metadata().get(SOME_HEADER).orElse("");
    return effects().reply(new Message(headerValue));
  }

  @Override
  public String applyEvent(Event event) {
    return currentState();
  }
}
