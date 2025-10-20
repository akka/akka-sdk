package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.timedaction.TimedAction;

@Component(id = "   ")
public class BlankComponentId extends TimedAction {
  public Effect test() {
    return effects().done();
  }
}
