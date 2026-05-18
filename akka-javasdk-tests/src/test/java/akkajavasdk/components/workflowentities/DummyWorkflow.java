/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
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
    return effects().updateState(20).transitionTo(DummyWorkflow::pause).thenReply("ok");
  }

  public StepEffect pause() {
    return stepEffects().thenPause();
  }

  public Effect<String> incrementAndPause(int amount) {
    return effects().updateState(currentState() + amount).pause().thenReply("ok");
  }

  public Effect<Integer> get() {
    return effects().reply(currentState());
  }
}
