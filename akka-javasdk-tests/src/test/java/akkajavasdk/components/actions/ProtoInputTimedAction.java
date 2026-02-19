/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.actions;

import akka.javasdk.annotations.Component;
import akka.javasdk.timedaction.TimedAction;
import akkajavasdk.StaticTestBuffer;
import akkajavasdk.protocol.SerializationTestProtos;

@Component(id = "proto-timed-action")
public class ProtoInputTimedAction extends TimedAction {

  public TimedAction.Effect someMessage(SerializationTestProtos.SimpleMessage message) {
    StaticTestBuffer.addValue(
        "proto-timed-action",
        message.getText() + ":" + message.getNumber() + ":" + message.getFlag());
    return effects().done();
  }
}
