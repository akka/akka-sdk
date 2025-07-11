/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.workflowentities;

import akkajavasdk.components.actions.echo.Message;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.workflow.Workflow;

import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;

@ComponentId("workflow-with-step-timeout")
public class WorkflowWithStepTimeout extends Workflow<FailingCounterState> {


  @Override
  public WorkflowConfig configuration() {
    return WorkflowConfig.builder()
      .workflowTimeout(ofSeconds(8))
      .defaultStepTimeout(ofMillis(20))
      .stepConfig(
        WorkflowWithStepTimeout::counterStep,
        maxRetries(1).failoverTo(WorkflowWithStepTimeout::counterFailover))
      .build();
  }

  public Effect<Message> startFailingCounter(String counterId) {
    return effects()
      .updateState(new FailingCounterState(counterId, 0, false))
      .transitionTo(WorkflowWithStepTimeout::counterStep)
      .thenReply(new Message("workflow started"));
  }

  private StepEffect counterStep()throws InterruptedException {
    Thread.sleep(1000); // force a delay to produce a timeout
    return stepEffects()
      .thenTransitionTo(WorkflowWithStepTimeout::counterFailover);
  }

  private StepEffect counterFailover() {
    var updatedState = currentState().inc();

    if (updatedState.value() == 2) {
      return stepEffects()
        .updateState(updatedState.asFinished())
        .thenEnd();

    } else {
      return stepEffects()
        .updateState(updatedState)
        .thenTransitionTo(WorkflowWithStepTimeout::counterStep);
    }
  }




  public Effect<FailingCounterState> get() {
    return effects().reply(currentState());
  }
}
