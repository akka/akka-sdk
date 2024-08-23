/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.actions.echo;

import akka.platform.javasdk.action.Action;
import akka.platform.javasdk.annotations.ComponentId;
import akka.platform.javasdk.annotations.ForwardHeaders;
import akka.platform.javasdk.client.ComponentClient;

// FIXME used in SpringSdkIntegrationTest, since component client is currently going over the rest endpoint
//       all headers expected to be forwarded must be opt-in. Once we switch to "native" component client
//       we will forward all metadata and this won't be needed
@ComponentId("with-metadata")
@ForwardHeaders({"myKey"})
public class ActionWithMetadata extends Action {

  private ComponentClient componentClient;

  public ActionWithMetadata(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public record KeyValue(String key, String value) {}

  public Effect returnMeta(String key) {
    var metaValue = messageContext().metadata().get(key).get();
    return effects().done();
  }
}
