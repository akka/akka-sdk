/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.actions.headers;

import com.example.wiring.actions.echo.Message;
import akka.platform.javasdk.action.Action;
import akka.platform.javasdk.annotations.ActionId;

/**
 * Action with the same name in a different package.
 */
@ActionId("echo2")
public class EchoAction extends Action {

  public Effect<Message> stringMessage(String msg) {
    return effects().reply(new Message(msg));
  }
}
