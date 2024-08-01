/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.actions.echo;

import akka.platform.javasdk.action.Action;
import akka.platform.javasdk.action.ActionContext;
import akka.platform.javasdk.annotations.ComponentId;
import akka.platform.javasdk.client.ComponentClient;

@ComponentId("shortened-echo")
public class ShortenedEchoAction extends Action {

  private ActionContext ctx;
  private ComponentClient componentClient;

  public ShortenedEchoAction(ActionContext ctx, ComponentClient componentClient) {
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
