/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.workflowentities;

import static java.time.Duration.ofMillis;

import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.workflow.Workflow;
import akkajavasdk.components.actions.echo.Message;
import java.time.Duration;

@Component(id = "workflow-with-timeout")
public class WorkflowWithTimeout extends Workflow<FailingCounterState> {

  private final ComponentClient componentClient;

  public WorkflowWithTimeout(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Override
  public WorkflowSettings settings() {
    return WorkflowSettings.builder()
        .defaultStepTimeout(ofMillis(999))
        .stepTimeout(WorkflowWithTimeout::counterStep, Duration.ofMillis(50))
        .stepRecovery(
            WorkflowWithTimeout::counterStep,
            maxRetries(1).failoverTo(WorkflowWithTimeout::counterFailoverStep).withInput(3))
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

    return stepEffects().updateState(currentState().asFinished()).thenEnd();
  }

  public Effect<FailingCounterState> get() {
    return effects().reply(currentState());
  }
}
