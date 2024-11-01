/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.actions.echo;

import akka.javasdk.timedaction.TimedAction;
import akka.javasdk.annotations.ComponentId;

import java.util.Collection;
import java.util.stream.Collectors;

@ComponentId("with-primitives")
//TODO remove or bring tests back
public class ActionWithPrimitives extends TimedAction {

  public Effect stringMessageWithOptionalParams(long longValue) {
    return effects().done();
  }

  public Effect stringMessage(double doubleValue) {
    String response = String.valueOf(doubleValue);
    return effects().done();
  }

  public Effect listMessage(Collection<Integer> ints) {
    String response = ints.stream().map(Object::toString).collect(Collectors.joining(","));
    return effects().done();
  }

}
