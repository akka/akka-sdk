/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.actions.echo;

import kalix.javasdk.action.Action;
import kalix.javasdk.action.ActionCreationContext;
import kalix.javasdk.annotations.ActionId;
import kalix.javasdk.client.ComponentClient;

@ActionId("shortened-echo")
public class ShortenedEchoAction extends Action {

  private ActionCreationContext ctx;
  private ComponentClient componentClient;

  public ShortenedEchoAction(ActionCreationContext ctx, ComponentClient componentClient) {
    this.ctx = ctx;
    this.componentClient = componentClient;
  }

  public Effect<Message> stringMessage(String msg) {
    var shortenedMsg = msg.replaceAll("[AEIOUaeiou]", "");
    var result = componentClient.forAction().method(EchoAction::stringMessage).invokeAsync(shortenedMsg);
    return effects().asyncReply(result);
  }

  public Effect<Message> leetShortUsingFwd(String msg) {
    var shortenedMsg = leetShort(msg);
    var result = componentClient.forAction().method(EchoAction::stringMessage).invokeAsync(shortenedMsg);
    return effects().asyncReply(result);
  }

  public Effect<Message> leetMessageFromPathUsingFwd(String msg) {
    return leetShortUsingFwd(msg);
  }


  public Effect<Message> leetMessageWithFwdPost(Message msg) {
    var shortenedMsg = leetShort(msg.text());
    var result = componentClient.forAction().method(EchoAction::stringMessage).invokeAsync(shortenedMsg);
    return effects().asyncReply(result);
  }

  private String leetShort(String msg) {
    return msg
            .replaceAll("[Ee]", "3")
            .replaceAll("[Aa]", "4")
            .replaceAll("[AEIOUaeiou]", "");
  }
}
