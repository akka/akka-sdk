/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.workflowentities.hierarchy;

import akka.javasdk.workflow.Workflow;

public abstract class AbstractTextKvWorkflow extends Workflow<AbstractTextKvWorkflow.State>
    implements ITextWorkflow {

  public record State(String value) {}

  protected StepEffect dummyStepInParent(String text) {
    return stepEffects()
        .thenTransitionTo(AbstractTextKvWorkflow::dummyStepInInterface)
        .withInput(text + "[abstract]");
  }

  @Override
  public StepEffect dummyStepInInterface(String text) {
    return stepEffects().updateState(new State(text + "[interface]")).thenEnd();
  }
}
