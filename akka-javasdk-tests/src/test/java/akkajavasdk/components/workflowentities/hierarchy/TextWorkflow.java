/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.workflowentities.hierarchy;

import akka.javasdk.annotations.ComponentId;
import java.util.Optional;

@ComponentId("hierarchy-workflow")
public class TextWorkflow extends AbstractTextKvWorkflow {


  public Effect<String> setText(String text) {
    // this Workflow will call a series of steps defined in
    // the concrete class, its parent and an interface
    // each call appends a text to the original message
    return effects()
      .transitionTo(TextWorkflow::dummyStep)
      .withInput(text)
      .thenReply("ok");
  }

  private StepEffect dummyStep(String text) {
    // step defined in parent should be callable
    return stepEffects()
      .thenTransitionTo(TextWorkflow::dummyStepInParent)
      .withInput(text + "[concrete]");
  }

  public Effect<Optional<String>> getText() {
    return effects().reply(Optional.ofNullable(currentState()).map(State::value));
  }
}
