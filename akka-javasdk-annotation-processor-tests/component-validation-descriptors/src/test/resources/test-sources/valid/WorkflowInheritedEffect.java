/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.workflow.Workflow;

class MyWorkflow extends Workflow<String> {

  public Effect<String> execute() {
    return effects().reply("ok");
  }

  public StepEffect processStep(String input) {

    return stepEffects().thenPause();

  }
}

@Component(id = "workflow-inherited-effect")
public class WorkflowInheritedEffect extends MyWorkflow {

}
