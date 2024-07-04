/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.actions.echo;

import akka.platform.javasdk.Metadata;
import akka.platform.javasdk.action.Action;
import akka.platform.javasdk.annotations.ActionId;
import akka.platform.javasdk.annotations.ForwardHeaders;
import akka.platform.javasdk.client.ComponentClient;

import java.util.concurrent.CompletableFuture;

// FIXME used in SpringSdkIntegrationTest, since component client is currently going over the rest endpoint
//       all headers expected to be forwarded must be opt-in. Once we switch to "native" component client
//       we will forward all metadata and this won't be needed
@ActionId("with-metadata")
@ForwardHeaders({"myKey"})
public class ActionWithMetadata extends Action {

  private ComponentClient componentClient;

  public ActionWithMetadata(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public record KeyValue(String key, String value) {}

  public Effect<Message> actionWithMeta(KeyValue keyValue) {
    var deferredCall =
      componentClient.forAction()
        .method(ActionWithMetadata::returnMeta)
        .withMetadata(Metadata.EMPTY.add(keyValue.key, keyValue.value))
        .deferred(keyValue.key);

    return effects().asyncReply(deferredCall.invokeAsync());
  }

  public Effect<Message> returnMeta(String key) {
    var metaValue = actionContext().metadata().get(key).get();
    return effects().reply(new Message(metaValue));
  }

  public Effect<Message> returnAsMeta(KeyValue keyValue) {
    var md = Metadata.EMPTY.add(keyValue.key, keyValue.value);
    return effects().reply(new Message(keyValue.value), md);
  }

  public Effect<Message> returnAsMetaAsync(KeyValue keyValue) {
    var md = Metadata.EMPTY.add(keyValue.key, keyValue.value);
    return effects().asyncReply(CompletableFuture.completedFuture(new Message(keyValue.value)), md);
  }
}
