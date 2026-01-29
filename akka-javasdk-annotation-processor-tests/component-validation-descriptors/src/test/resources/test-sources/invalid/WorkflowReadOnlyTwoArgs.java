/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.workflow.Workflow;

@Component(id = "workflow-readonly-two-args")
public class WorkflowReadOnlyTwoArgs extends Workflow<String> {

  // Regular command handler  - required
  public Effect<String> execute(String cmd) {
    return effects().reply(cmd);
  }

  // ReadOnlyEffect handler with 2 arguments - not allowed
  public ReadOnlyEffect<String> query(String cmd, int i) {
    return effects().reply(cmd);
  }
}
