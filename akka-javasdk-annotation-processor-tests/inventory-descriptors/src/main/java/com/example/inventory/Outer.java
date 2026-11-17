/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.inventory;

import akka.javasdk.annotations.Component;
import akka.javasdk.timedaction.TimedAction;

public class Outer {

  @Component(id = "nested-action")
  public static class NestedAction extends TimedAction {

    public Effect run() {
      return effects().done();
    }
  }
}
