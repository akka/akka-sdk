/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.timedaction.TimedAction;

@Component(id = "hello")
public class HelloAction extends TimedAction {

  public Effect hello() {
    return effects().done();
  }
}
