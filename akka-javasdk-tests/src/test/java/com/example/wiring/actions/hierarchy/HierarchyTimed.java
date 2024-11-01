/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.actions.hierarchy;

import akka.javasdk.annotations.ComponentId;
import com.example.wiring.StaticTestBuffer;

@ComponentId("hierarchy-action")
public class HierarchyTimed extends AbstractTimed {
  public Effect stringMessage(String msg) {
    StaticTestBuffer.addValue("hierarchy-action", msg);
    return effects().done();
  }
}
