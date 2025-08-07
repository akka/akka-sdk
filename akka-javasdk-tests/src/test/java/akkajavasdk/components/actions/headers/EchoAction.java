/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.actions.headers;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.timedaction.TimedAction;

/** Action with the same class name in a different package. */
@SuppressWarnings("unused") // not here to be called, but to test conflicting names
@ComponentId("echo2")
public class EchoAction extends TimedAction {

  public Effect stringMessage(String msg) {
    return effects().done();
  }
}
