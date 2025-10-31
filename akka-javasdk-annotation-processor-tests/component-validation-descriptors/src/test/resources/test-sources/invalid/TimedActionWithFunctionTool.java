package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.timedaction.TimedAction;

@Component(id = "timed-action-with-function-tool")
public class TimedActionWithFunctionTool extends TimedAction {

  @FunctionTool(description = "This should not be allowed")
  public Effect doSomething() {
    return effects().done();
  }
}
