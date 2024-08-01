/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.actions.headers;

import akka.platform.javasdk.annotations.ComponentId;
import com.example.wiring.actions.echo.Message;
import akka.platform.javasdk.action.Action;
import akka.platform.javasdk.annotations.ForwardHeaders;

@ComponentId("forward-headers-action")
@ForwardHeaders(ForwardHeadersAction.SOME_HEADER)
public class ForwardHeadersAction extends Action {

  public static final String SOME_HEADER = "some-header";

  public Effect<Message> stringMessage() {
    String headerValue = messageContext().metadata().get(SOME_HEADER).orElse("");
    return effects().reply(new Message(headerValue));
  }
}
