/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.workflowentities.hierarchy;

import akka.javasdk.annotations.ComponentId;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@ComponentId("hierarchy-workflow")
public class TextWorkflow extends AbstractTextKvWorkflow {
  @Override
  public WorkflowDef<State> definition() {
    return workflow().addStep(step("dummy-step")
        .asyncCall(() ->
            // never completes
            CompletableFuture.completedFuture("step completed"))
        .andThen(String.class, result -> effects().end()));

  }

  public Effect<String> setText(String text) {
    return effects().updateState(new State(text)).transitionTo("dummy-step").thenReply("ok");
  }

  public Effect<Optional<String>> getText() {
    return effects().reply(Optional.ofNullable(currentState()).map(State::value));
  }
}
