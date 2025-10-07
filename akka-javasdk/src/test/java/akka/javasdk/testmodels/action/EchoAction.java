/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testmodels.action;

import akka.javasdk.annotations.Component;
import akka.javasdk.timedaction.TimedAction;

@Component(id = "test-echo")
public class EchoAction extends TimedAction {

  public Effect stringMessage(String msg) {
    return effects().done();
  }
}
