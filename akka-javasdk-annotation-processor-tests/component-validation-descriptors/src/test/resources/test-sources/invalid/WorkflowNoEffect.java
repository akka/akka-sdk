/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.workflow.Workflow;

@Component(id = "workflow-no-effect")
public class WorkflowNoEffect extends Workflow<String> {

  // No Effect method - this should cause a validation error
  public String execute() {
    return "ok";
  }
}
