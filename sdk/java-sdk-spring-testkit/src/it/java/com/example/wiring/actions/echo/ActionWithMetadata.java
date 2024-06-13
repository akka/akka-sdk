/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.actions.echo;

import kalix.javasdk.Metadata;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.ForwardHeaders;
import kalix.javasdk.client.ComponentClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.concurrent.CompletableFuture;

// FIXME used in SpringSdkIntegrationTest, since component client is currently going over the rest endpoint
//       all headers expected to be forwarded must be opt-in. Once we switch to "native" component client
//       we will forward all metadata and this won't be needed
@ForwardHeaders({"myKey"})
public class ActionWithMetadata extends Action {

  private ComponentClient componentClient;

  public ActionWithMetadata(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @GetMapping("/action-with-meta/{key}/{value}")
  public Effect<Message> actionWithMeta(@PathVariable String key, @PathVariable String value) {
    var deferredCall =
      componentClient.forAction()
        .method(ActionWithMetadata::returnMeta)
        .withMetadata(Metadata.EMPTY.add(key, value))
        .deferred(key);

    return effects().asyncReply(deferredCall.invokeAsync());
  }

  @GetMapping("/return-meta/{key}")
  public Effect<Message> returnMeta(@PathVariable String key) {
    var metaValue = actionContext().metadata().get(key).get();
    return effects().reply(new Message(metaValue));
  }

  @GetMapping("/reply-meta/{key}/{value}")
  public Effect<Message> returnAsMeta(@PathVariable String key, @PathVariable String value) {
    var md = Metadata.EMPTY.add(key, value);
    return effects().reply(new Message(value), md);
  }

  @GetMapping("/reply-async-meta/{key}/{value}")
  public Effect<Message> returnAsMetaAsync(@PathVariable String key, @PathVariable String value) {
    var md = Metadata.EMPTY.add(key, value);
    return effects().asyncReply(CompletableFuture.completedFuture(new Message(value)), md);
  }
}
