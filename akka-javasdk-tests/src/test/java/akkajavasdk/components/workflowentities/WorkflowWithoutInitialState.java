/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.workflowentities;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.workflow.Workflow;

@ComponentId("workflow-without-initial-state")
public class WorkflowWithoutInitialState extends Workflow<String> {

  public Effect<String> start() {
    return effects().transitionTo(WorkflowWithoutInitialState::test).thenReply("ok");
  }

  private StepEffect test() {
    return stepEffects().updateState("success").thenEnd();
  }

  public Effect<String> get() {
    if (currentState() == null) {
      return effects().reply("empty");
    } else {
      return effects().reply(currentState());
    }
  }
}
