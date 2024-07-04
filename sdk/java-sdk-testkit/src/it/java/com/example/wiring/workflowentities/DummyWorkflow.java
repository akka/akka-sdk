/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.workflowentities;

import akka.platform.javasdk.annotations.TypeId;
import akka.platform.javasdk.workflow.Workflow;

@TypeId("dummy-workflow")
public class DummyWorkflow extends Workflow<Integer> {

  @Override
  public WorkflowDef<Integer> definition() {
    return workflow();
  }

  public Effect<String> startAndFinish() {
    return effects().updateState(10).end().thenReply("ok");
  }

  public Effect<String> update() {
    return effects().updateState(20).transitionTo("asd").thenReply("ok");
  }

  public Effect<Integer> get() {
    return effects().reply(currentState());
  }
}
