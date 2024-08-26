/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.actions.echo;

import akka.platform.javasdk.timedaction.TimedAction;
import akka.platform.javasdk.timedaction.TimedActionContext;
import akka.platform.javasdk.annotations.ComponentId;
import akka.platform.javasdk.client.ComponentClient;
import com.example.wiring.actions.headers.TestBuffer;

@ComponentId("echo")
public class EchoAction extends TimedAction {

  private TimedActionContext ctx;
  private final ComponentClient componentClient;

  public EchoAction(TimedActionContext ctx, ComponentClient componentClient) {
    this.ctx = ctx;
    this.componentClient = componentClient;
  }

  public Effect stringMessage(String msg) {
    TestBuffer.addValue("echo-action", msg);
    return effects().done();
  }
}
