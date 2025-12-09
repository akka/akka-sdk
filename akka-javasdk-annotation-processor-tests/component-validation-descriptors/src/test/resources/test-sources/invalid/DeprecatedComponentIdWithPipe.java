package com.example;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.timedaction.TimedAction;

@ComponentId("invalid|id")
public class DeprecatedComponentIdWithPipe extends TimedAction {
  public Effect test() {
    return effects().done();
  }
}
