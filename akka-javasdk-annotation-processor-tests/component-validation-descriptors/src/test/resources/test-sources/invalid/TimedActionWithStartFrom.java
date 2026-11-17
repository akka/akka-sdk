package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.timedaction.TimedAction;

@Component(id = "timed-action-start-from")
@Consume.StartFrom(latest = true)
public class TimedActionWithStartFrom extends TimedAction {

  public Effect doSomething() {
    return effects().done();
  }
}
