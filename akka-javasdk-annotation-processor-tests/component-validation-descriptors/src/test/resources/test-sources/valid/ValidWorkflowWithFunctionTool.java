/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.workflow.Workflow;

@Component(id = "valid-workflow-with-function-tool")
public class ValidWorkflowWithFunctionTool extends Workflow<String> {

  @FunctionTool(description = "This is allowed on Effect")
  public Effect execute() {
    return effects().reply("ok");
  }

  @FunctionTool(description = "This is allowed on ReadOnlyEffect")
  public ReadOnlyEffect query() {
    return effects().reply("query result");
  }
}
