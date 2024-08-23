/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package spring.testmodels.actions;

import akka.platform.javasdk.action.Action;

public class SimpleAction extends Action {

  public Effect echo(String msg) {
    return effects().done();
  }
}
