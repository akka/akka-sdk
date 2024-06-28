/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package kalix.spring.testmodels.action;

import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.ActionId;
import kalix.spring.testmodels.Message;

@ActionId("test-echo")
public class EchoAction extends Action {

  public Effect<Message> stringMessage(String msg) {
    return effects().reply(new Message(msg));
  }

}
