/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.workflowentities;

import akka.javasdk.annotations.Component;
import akka.javasdk.workflow.Workflow;

@Component(id = "dummy-workflow")
public class DummyWorkflow extends Workflow<Integer> {

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
