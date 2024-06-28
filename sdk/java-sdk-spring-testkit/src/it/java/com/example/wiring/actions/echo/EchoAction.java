/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.actions.echo;

import kalix.javasdk.action.Action;
import kalix.javasdk.action.ActionCreationContext;
import kalix.javasdk.annotations.ActionId;
import kalix.javasdk.client.ComponentClient;

@ActionId("echo")
public class EchoAction extends Action {

  private Parrot parrot;
  private ActionCreationContext ctx;
  private final ComponentClient componentClient;

  public EchoAction(ActionCreationContext ctx,  ComponentClient componentClient) {
    this.parrot = new Parrot();
    this.ctx = ctx;
    this.componentClient = componentClient;
  }

  public Effect<Message> stringMessage(String msg) {
    String response = this.parrot.repeat(msg);
    return effects().reply(new Message(response));
  }
}
