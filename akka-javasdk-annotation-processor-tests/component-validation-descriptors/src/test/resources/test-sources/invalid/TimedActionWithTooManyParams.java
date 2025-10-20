package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.timedaction.TimedAction;

@Component(id = "timed-action-too-many-params")
public class TimedActionWithTooManyParams extends TimedAction {

  public Effect validMethod() {
    return effects().done();
  }

  public Effect invalidMethod(String param1, String param2) {
    // Invalid: has more than one parameter
    return effects().done();
  }
}
