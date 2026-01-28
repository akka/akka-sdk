/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.annotations.Component;
import akka.javasdk.workflow.Workflow;

@Component(id = "valid-workflow-one-arg")
public class ValidWorkflowOneArg extends Workflow<String> {

  public Effect<String> execute(String command) {
    return effects().reply(command);
  }
}
