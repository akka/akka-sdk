/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.spring.testmodels.action;

import akka.platform.javasdk.action.Action;
import akka.platform.javasdk.annotations.ComponentId;

@ComponentId("test-echo")
public class EchoAction extends Action {

  public Effect stringMessage(String msg) {
    return effects().done();
  }

}
