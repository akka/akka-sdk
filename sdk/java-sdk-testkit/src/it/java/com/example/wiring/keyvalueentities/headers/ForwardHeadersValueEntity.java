/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.keyvalueentities.headers;

import akka.platform.javasdk.annotations.ComponentId;
import akka.platform.javasdk.annotations.ForwardHeaders;
import akka.platform.javasdk.keyvalueentity.KeyValueEntity;
import com.example.wiring.actions.echo.Message;

import static com.example.wiring.keyvalueentities.headers.ForwardHeadersValueEntity.SOME_HEADER;

@ComponentId("forward-headers-ve")
@ForwardHeaders(SOME_HEADER)
public class ForwardHeadersValueEntity extends KeyValueEntity<String> {

  public static final String SOME_HEADER = "some-header";

  public Effect<Message> createUser() {
    String headerValue = commandContext().metadata().get(SOME_HEADER).orElse("");
    return effects().reply(new Message(headerValue));
  }
}
