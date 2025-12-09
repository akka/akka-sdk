/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.timedaction.TimedAction;

@Component(id = "valid-timed-action-one-arg")
public class ValidTimedActionOneArg extends TimedAction {

  public Effect doSomething(String param) {
    return effects().done();
  }
}
