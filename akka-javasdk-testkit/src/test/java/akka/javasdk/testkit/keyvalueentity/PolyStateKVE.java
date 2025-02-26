/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.keyvalueentity;

import akka.javasdk.keyvalueentity.KeyValueEntity;
import akka.javasdk.testkit.eventsourced.PolyState;

public class PolyStateKVE extends KeyValueEntity<PolyState> {

  public Effect<String> handleCommand() {
    return effects().updateState(new PolyState.StateA()).thenReply("");
  }
}
