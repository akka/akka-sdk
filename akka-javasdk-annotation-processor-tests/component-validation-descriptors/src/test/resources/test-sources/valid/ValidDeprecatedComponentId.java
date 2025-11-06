package com.example;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.timedaction.TimedAction;

@ComponentId("valid-deprecated-id")
public class ValidDeprecatedComponentId extends TimedAction {
  public Effect test() {
    return effects().done();
  }
}
