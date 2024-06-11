/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import kalix.javasdk.action.Action;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/hello")
public class HelloAction extends Action {

  @GetMapping
  public Effect<String> hello() {
    return effects().reply("Hello, World!");
  }
}
