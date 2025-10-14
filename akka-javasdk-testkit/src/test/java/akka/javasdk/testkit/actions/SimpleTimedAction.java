/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.actions;

import akka.javasdk.timedaction.TimedAction;

public class SimpleTimedAction extends TimedAction {

  public Effect echo(String msg) {
    return effects().done();
  }
}
