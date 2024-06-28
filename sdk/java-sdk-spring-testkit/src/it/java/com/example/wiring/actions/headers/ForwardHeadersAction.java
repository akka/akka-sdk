/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.actions.headers;

import com.example.wiring.actions.echo.Message;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.ForwardHeaders;

@ForwardHeaders(ForwardHeadersAction.SOME_HEADER)
public class ForwardHeadersAction extends Action {

  public static final String SOME_HEADER = "some-header";

  public Effect<Message> stringMessage() {
    String headerValue = actionContext().metadata().get(SOME_HEADER).orElse("");
    return effects().reply(new Message(headerValue));
  }
}
