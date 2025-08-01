/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testmodels.action;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.timedaction.TimedAction;

public class ActionsTestModels {

  @ComponentId("test-action-0")
  public static class ActionWithoutParam extends TimedAction {
    public Effect message() {
      return effects().done();
    }
  }

  @ComponentId("test-action-1")
  public static class ActionWithOneParam extends TimedAction {
    public Effect message(String one) {
      return effects().done();
    }
  }
}
