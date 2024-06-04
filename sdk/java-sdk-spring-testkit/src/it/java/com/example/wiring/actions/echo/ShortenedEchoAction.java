/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.actions.echo;

import kalix.javasdk.action.Action;
import kalix.javasdk.action.ActionCreationContext;
import kalix.javasdk.client.ComponentClient;
import org.springframework.web.bind.annotation.*;

public class ShortenedEchoAction extends Action {

  private ActionCreationContext ctx;
  private ComponentClient componentClient;

  public ShortenedEchoAction(ActionCreationContext ctx, ComponentClient componentClient) {
    this.ctx = ctx;
    this.componentClient = componentClient;
  }

  @GetMapping("/echo/message/{msg}/short")
  public Effect<Message> stringMessage(@PathVariable String msg) {
    var shortenedMsg = msg.replaceAll("[AEIOUaeiou]", "");
    var result = componentClient.forAction().method(EchoAction::stringMessage).deferred(shortenedMsg).invokeAsync();
    return effects().asyncReply(result);
  }

  @GetMapping("/echo/message-short")
  public Effect<Message> leetShortUsingFwd(@RequestParam String msg) {
    var shortenedMsg = leetShort(msg);
    var result = componentClient.forAction().method(EchoAction::stringMessageFromParam).deferred(shortenedMsg);
    return effects().forward(result);
  }

  @GetMapping("/echo/message/{msg}/leetshort")
  public Effect<Message> leetMessageFromPathUsingFwd(@PathVariable String msg) {
    return leetShortUsingFwd(msg);
  }

  @PostMapping("/echo/message/leetshort")
  public Effect<Message> leetMessageWithFwdPost(@RequestBody Message msg) {
    var shortenedMsg = leetShort(msg.text());
    var result = componentClient.forAction().method(EchoAction::stringMessage).deferred(shortenedMsg);
    return effects().forward(result);
  }

  private String leetShort(String msg) {
    return msg
            .replaceAll("[Ee]", "3")
            .replaceAll("[Aa]", "4")
            .replaceAll("[AEIOUaeiou]", "");
  }
}
