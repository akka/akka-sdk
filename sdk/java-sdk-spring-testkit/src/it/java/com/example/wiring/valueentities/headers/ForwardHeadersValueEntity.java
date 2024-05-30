/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.valueentities.headers;

import com.example.wiring.actions.echo.Message;
import kalix.javasdk.annotations.ForwardHeaders;
import kalix.javasdk.annotations.TypeId;
import kalix.javasdk.valueentity.ValueEntity;

import static com.example.wiring.actions.headers.ForwardHeadersAction.SOME_HEADER;

@TypeId("forward-headers-ve")
@ForwardHeaders(SOME_HEADER)
public class ForwardHeadersValueEntity extends ValueEntity<String> {

  public Effect<Message> createUser() {
    String headerValue = commandContext().metadata().get(SOME_HEADER).orElse("");
    return effects().reply(new Message(headerValue));
  }
}
