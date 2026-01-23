/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.workflow.Workflow;

@Component(id = "valid-workflow-step-no-arg")
public class ValidWorkflowStepNoArg extends Workflow<String> {

  public Effect<String> execute() {
    return effects().reply("ok");
  }

  public StepEffect processStep() {
    return stepEffects().thenPause();
  }
}
