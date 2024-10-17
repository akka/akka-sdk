/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testmodels.action;

import akka.javasdk.timedaction.TimedAction;
import akka.javasdk.annotations.ComponentId;

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
