/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.eventsourcedentities.headers;

import com.example.wiring.actions.echo.Message;
import kalix.javasdk.annotations.ForwardHeaders;
import kalix.javasdk.annotations.TypeId;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;

import static com.example.wiring.actions.headers.ForwardHeadersAction.SOME_HEADER;

@TypeId("forward-headers-es")
@ForwardHeaders(SOME_HEADER)
public class ForwardHeadersESEntity extends EventSourcedEntity<String, Object> {

  public Effect<Message> createUser() {
    String headerValue = commandContext().metadata().get(SOME_HEADER).orElse("");
    return effects().reply(new Message(headerValue));
  }
}
