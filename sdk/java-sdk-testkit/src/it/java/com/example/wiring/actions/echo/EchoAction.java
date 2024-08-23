/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.actions.echo;

import akka.platform.javasdk.action.Action;
import akka.platform.javasdk.action.ActionContext;
import akka.platform.javasdk.annotations.ComponentId;
import akka.platform.javasdk.client.ComponentClient;
import com.example.wiring.actions.headers.TestBuffer;

@ComponentId("echo")
public class EchoAction extends Action {

  private ActionContext ctx;
  private final ComponentClient componentClient;

  public EchoAction(ActionContext ctx, ComponentClient componentClient) {
    this.ctx = ctx;
    this.componentClient = componentClient;
  }

  public Effect stringMessage(String msg) {
    TestBuffer.addValue("echo-action", msg);
    return effects().done();
  }
}
