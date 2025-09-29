/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.actions.hierarchy;

import akka.javasdk.annotations.Component;
import akkajavasdk.StaticTestBuffer;

@Component(id = "hierarchy-action")
public class HierarchyTimed extends AbstractTimed {
  public Effect stringMessage(String msg) {
    StaticTestBuffer.addValue("hierarchy-action", msg);
    return effects().done();
  }
}
