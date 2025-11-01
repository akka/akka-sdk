/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.workflow.Workflow;

@Component(id = "workflow-two-args")
public class WorkflowTwoArgs extends Workflow<String> {

  // Command handler with 2 arguments - not allowed
  public Effect execute(String cmd, int i) {
    return effects().reply(cmd);
  }
}
