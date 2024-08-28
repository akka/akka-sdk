/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.testmodels.actions;

import akka.javasdk.timedaction.TimedAction;

public class SimpleAction extends TimedAction {

  public Effect echo(String msg) {
    return effects().done();
  }
}
