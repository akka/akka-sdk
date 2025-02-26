/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eventsourced;

import akka.javasdk.eventsourcedentity.EventSourcedEntity;

public class PolyStateESE extends EventSourcedEntity<PolyState, CounterEvent> {

  public Effect<String> commandHandlerWithResponse() {
    return effects().reply("reply");
  }

  @Override
  public PolyState applyEvent(CounterEvent event) {
    return null;
  }
}
