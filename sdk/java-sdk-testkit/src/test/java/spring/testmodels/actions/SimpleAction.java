/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package spring.testmodels.actions;

import akka.platform.javasdk.timedaction.TimedAction;

public class SimpleAction extends TimedAction {

  public Effect echo(String msg) {
    return effects().done();
  }
}
