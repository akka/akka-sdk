/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.actions.echo;

import akka.javasdk.timedaction.TimedAction;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.client.ComponentClient;
import com.example.wiring.actions.headers.TestBuffer;

@ComponentId("echo")
public class EchoAction extends TimedAction {

  private final ComponentClient componentClient;

  public EchoAction(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect stringMessage(String msg) {
    TestBuffer.addValue("echo-action", msg);
    return effects().done();
  }
}
