/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.workflowentities;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.workflow.Workflow;

@ComponentId("workflow-without-initial-state")
public class WorkflowWithoutInitialState extends Workflow<String> {

  @Override
  public WorkflowDef<String> definition() {
    var test =
        step("test")
            .call(() -> "ok")
            .andThen(String.class, result -> effects().updateState("success").end());

    return workflow().addStep(test);
  }

  public Effect<String> start() {
    return effects().transitionTo("test").thenReply("ok");
  }

  public Effect<String> get() {
    if (currentState() == null) {
      return effects().reply("empty");
    } else {
      return effects().reply(currentState());
    }
  }
}
