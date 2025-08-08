/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.timedaction.TimedAction;

@ComponentId("hello")
public class HelloAction extends TimedAction {

  public Effect hello() {
    return effects().done();
  }
}
