package com.example;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.timedaction.TimedAction;

@ComponentId("   ")
public class BlankDeprecatedComponentId extends TimedAction {
  public Effect test() {
    return effects().done();
  }
}
