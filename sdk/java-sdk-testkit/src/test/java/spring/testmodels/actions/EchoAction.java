/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package spring.testmodels.actions;

import akka.platform.javasdk.action.Action;

public class EchoAction extends Action {

  public Effect<String> echo(String msg) {
    return effects().reply(msg);
  }

  public Effect<String> echoWithMetadata(String msg) {
    return effects().reply(actionContext().metadata().get("key").get());
  }
}
