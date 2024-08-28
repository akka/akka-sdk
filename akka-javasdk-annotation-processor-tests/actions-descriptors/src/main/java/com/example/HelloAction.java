/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.timedaction.TimedAction;
import akka.javasdk.annotations.ComponentId;

@ComponentId("hello")
public class HelloAction extends TimedAction {

  public Effect hello() {
    return effects().done();
  }
}
