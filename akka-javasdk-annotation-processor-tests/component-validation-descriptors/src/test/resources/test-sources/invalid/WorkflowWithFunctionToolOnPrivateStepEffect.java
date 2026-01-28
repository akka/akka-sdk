/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.workflow.Workflow;

@Component(id = "workflow-with-function-tool-on-private-step-effect")
public class WorkflowWithFunctionToolOnPrivateStepEffect extends Workflow<String> {

  @FunctionTool(description = "This should not be allowed on StepEffect")
  private StepEffect invalidStep() {
    return effects().done();
  }

  public Effect<String> execute() {
    return effects().reply("ok");
  }
}
