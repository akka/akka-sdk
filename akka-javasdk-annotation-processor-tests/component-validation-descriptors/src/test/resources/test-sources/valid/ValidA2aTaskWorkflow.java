/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.Done;
import akka.javasdk.a2a.A2aMessage;
import akka.javasdk.a2a.A2aTaskContext;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.a2a.A2aSkill;
import akka.javasdk.workflow.Workflow;

@Component(id = "valid-task-workflow")
@A2aSkill(id = "review", name = "Review")
public class ValidA2aTaskWorkflow extends Workflow<String> {

  private final A2aTaskContext task;

  public ValidA2aTaskWorkflow(A2aTaskContext task) {
    this.task = task;
  }

  public Effect<Done> onMessage(A2aMessage message) {
    return effects().transitionTo(ValidA2aTaskWorkflow::review).thenReply(Done.getInstance());
  }

  private StepEffect review() {
    task.completed("Reviewed");
    return stepEffects().thenEnd();
  }
}
