/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.ActionId;

@ActionId("hello")
public class HelloAction extends Action {

  public Effect<String> hello() {
    return effects().reply("Hello, World!");
  }
}
