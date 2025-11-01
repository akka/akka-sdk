package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.timedaction.TimedAction;

@Component(id = "valid-timed-action")
public class ValidTimedAction extends TimedAction {

  public Effect doSomething() {
    return effects().done();
  }

  public Effect doSomethingElse(String param) {
    return effects().done();
  }
}
