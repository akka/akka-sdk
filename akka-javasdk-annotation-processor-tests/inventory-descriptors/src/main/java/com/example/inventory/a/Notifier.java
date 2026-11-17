/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.inventory.a;

import akka.javasdk.annotations.Component;
import akka.javasdk.timedaction.TimedAction;

@Component(id = "notifier-a")
public class Notifier extends TimedAction {

  public Effect send(String message) {
    return effects().done();
  }
}
