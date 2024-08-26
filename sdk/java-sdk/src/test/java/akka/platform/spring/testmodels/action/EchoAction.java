/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.spring.testmodels.action;

import akka.platform.javasdk.timedaction.TimedAction;
import akka.platform.javasdk.annotations.ComponentId;

@ComponentId("test-echo")
public class EchoAction extends TimedAction {

  public Effect stringMessage(String msg) {
    return effects().done();
  }

}
