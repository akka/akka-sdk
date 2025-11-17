/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.timedaction.TimedAction;

@Component(id = "timed-action-with-function-tool-on-private-method")
public class TimedActionWithFunctionToolOnPrivateMethod extends TimedAction {

  public Effect execute() {
    return effects().done();
  }

  // @FunctionTool is not allowed on private methods
  @FunctionTool(description = "This should not be allowed on private methods")
  private Effect privateMethod() {
    return effects().done();
  }
}
