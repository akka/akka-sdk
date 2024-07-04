/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.actions.echo;

import akka.platform.javasdk.action.Action;
import akka.platform.javasdk.annotations.ActionId;

import java.util.Collection;
import java.util.stream.Collectors;

@ActionId("with-primitives")
public class ActionWithPrimitives extends Action {

  public Effect<Message> stringMessageWithOptionalParams(long longValue) {
    return effects().reply(new Message(String.valueOf(longValue)));
  }

  public Effect<Message> stringMessage(double doubleValue) {
    String response = String.valueOf(doubleValue);
    return effects().reply(new Message(response));
  }

  public Effect<Message> listMessage(Collection<Integer> ints) {
    String response = ints.stream().map(Object::toString).collect(Collectors.joining(","));

    return effects().reply(new Message(response));
  }

}
