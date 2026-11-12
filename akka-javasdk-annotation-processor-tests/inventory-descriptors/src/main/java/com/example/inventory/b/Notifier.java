/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.inventory.b;

import akka.javasdk.annotations.Component;
import akka.javasdk.timedaction.TimedAction;

@Component(id = "notifier-b")
public class Notifier extends TimedAction {

  public Effect send(String message) {
    return effects().done();
  }
}
