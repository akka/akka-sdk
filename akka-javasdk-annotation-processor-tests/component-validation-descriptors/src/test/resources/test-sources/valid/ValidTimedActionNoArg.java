/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.timedaction.TimedAction;

@Component(id = "valid-timed-action-no-arg")
public class ValidTimedActionNoArg extends TimedAction {

  public Effect doSomething() {
    return effects().done();
  }
}
