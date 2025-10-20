package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.timedaction.TimedAction;

@Component(id = "timed-action-without-effect")
public class TimedActionWithoutEffect extends TimedAction {
  // No methods returning Effect - this should fail validation

  public void doSomething() {
    // Invalid: returns void instead of Effect
  }
}
