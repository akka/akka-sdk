/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.workflow.Workflow;

@Component(id = "workflow-step-two-args")
public class WorkflowStepTwoArgs extends Workflow<String> {

  public Effect execute() {
    return effects().reply("ok");
  }

  // Step method with 2 arguments - not allowed
  private StepEffect processStep(String input, int count) {
    return effects().pause();
  }
}
