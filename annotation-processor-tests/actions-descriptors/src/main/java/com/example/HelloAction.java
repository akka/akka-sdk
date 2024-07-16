/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.platform.javasdk.action.Action;
import akka.platform.javasdk.annotations.ComponentId;

@ComponentId("hello")
public class HelloAction extends Action {

  public Effect<String> hello() {
    return effects().reply("Hello, World!");
  }
}
