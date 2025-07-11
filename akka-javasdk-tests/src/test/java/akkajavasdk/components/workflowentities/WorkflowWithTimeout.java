/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.workflowentities;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.workflow.Workflow;
import akkajavasdk.components.actions.echo.Message;

import java.time.Duration;

import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;

@ComponentId("workflow-with-timeout")
public class WorkflowWithTimeout extends Workflow<FailingCounterState> {


  private final ComponentClient componentClient;

  public WorkflowWithTimeout(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Override
  public WorkflowConfig configuration() {
    return WorkflowConfig.builder()
      .workflowTimeout(ofSeconds(1))
      .workflowRecovery(
        maxRetries(1).failoverTo(WorkflowWithTimeout::counterFailoverStep).withInput(3))
      .defaultStepTimeout(ofMillis(999))
      .stepConfig(
        WorkflowWithTimeout::counterStep,
        Duration.ofMillis(50),
        maxRetries(1).failoverTo(WorkflowWithTimeout::counterStep))
      .build();

  }

  public Effect<Message> startFailingCounter(String counterId) {
    return effects()
      .updateState(new FailingCounterState(counterId, 0, false))
      .transitionTo(WorkflowWithTimeout::counterStep)
      .thenReply(new Message("workflow started"));
  }


  private StepEffect counterStep() throws InterruptedException {
    Thread.sleep(1000); // force a delay to produce a timeout
    return stepEffects().thenEnd();
  }

  private StepEffect counterFailoverStep(int num) {
    componentClient
      .forEventSourcedEntity(currentState().counterId())
      .method(FailingCounterEntity::increase)
      .invoke(num);

    return stepEffects()
      .updateState(currentState().asFinished())
      .thenTransitionTo(WorkflowWithTimeout::counterStep);
  }

  public Effect<FailingCounterState> get() {
    return effects().reply(currentState());
  }
}
