/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.inventory;

import akka.javasdk.annotations.Component;
import akka.javasdk.timedaction.TimedAction;

@Component(id = "cleanup")
public class CleanupAction extends TimedAction {

  public Effect cleanup() {
    return effects().done();
  }
}
