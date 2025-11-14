/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.workflow.Workflow;

@Component(id = "workflow-with-function-tool-on-private-method")
public class WorkflowWithFunctionToolOnPrivateMethod extends Workflow<String> {

  public Effect<String> start(String input) {
    return effects().updateState(input).end();
  }

  // @FunctionTool is not allowed on private methods
  @FunctionTool(description = "This should not be allowed on private methods")
  private Effect<String> privateMethod() {
    return effects().reply("private");
  }
}
