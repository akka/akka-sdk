/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.actions.echo;

import akka.javasdk.timedaction.TimedAction;
import akka.javasdk.annotations.ComponentId;
import com.example.wiring.StaticTestBuffer;

@ComponentId("with-metadata")
public class ActionWithMetadata extends TimedAction {

  public static final String SOME_HEADER = "some-header";

  public Effect processWithMeta() {
    String headerValue = commandContext().metadata().get(SOME_HEADER).orElse("");
    StaticTestBuffer.addValue(SOME_HEADER, headerValue);
    return effects().done();
  }
}
