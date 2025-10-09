/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.actions.echo;

import akka.javasdk.annotations.Component;
import akka.javasdk.timedaction.TimedAction;
import akkajavasdk.StaticTestBuffer;

@Component(id = "with-metadata")
public class ActionWithMetadata extends TimedAction {

  public static final String SOME_HEADER = "some-header";

  public Effect processWithMeta() {
    String headerValue = commandContext().metadata().get(SOME_HEADER).orElse("");
    StaticTestBuffer.addValue(SOME_HEADER, headerValue);
    return effects().done();
  }
}
