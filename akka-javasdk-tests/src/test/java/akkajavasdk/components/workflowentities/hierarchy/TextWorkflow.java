/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.workflowentities.hierarchy;

import akka.javasdk.annotations.ComponentId;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@ComponentId("hierarchy-workflow")
public class TextWorkflow extends AbstractTextKvWorkflow {

  private StepEffect dummyStep(String text) {
    return stepEffects()
      .thenTransitionTo(TextWorkflow::dummyAfterStep).withInput(text);
  }

  public Effect<String> setText(String text) {
    return effects()
      .transitionTo(TextWorkflow::dummyStep).withInput(text)
      .thenReply("ok");
  }

  public Effect<Optional<String>> getText() {
    return effects().reply(Optional.ofNullable(currentState()).map(State::value));
  }
}
