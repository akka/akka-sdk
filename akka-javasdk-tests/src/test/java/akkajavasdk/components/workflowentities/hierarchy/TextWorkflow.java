/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.workflowentities.hierarchy;

import akka.javasdk.annotations.ComponentId;

import java.util.Optional;

@ComponentId("hierarchy-workflow")
public class TextWorkflow extends AbstractTextKvWorkflow {


  public Effect<String> setText(String text) {
    return effects()
      .transitionTo(TextWorkflow::dummyStep)
      .withInput(text)
      .thenReply("ok");
  }

  private StepEffect dummyStep(String text) {
    // step defined in parent should be callable
    return stepEffects()
      .thenTransitionTo(TextWorkflow::dummyStepInParent)
      .withInput(text);
  }

  public Effect<Optional<String>> getText() {
    return effects().reply(Optional.ofNullable(currentState()).map(State::value));
  }
}
