/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.workflowentities;

import static java.time.Duration.ofSeconds;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.workflow.Workflow;
import akkajavasdk.components.actions.echo.Message;

@ComponentId("workflow-with-recover-strategy")
public class WorkflowWithRecoverStrategy extends Workflow<FailingCounterState> {

  private final ComponentClient componentClient;

  public WorkflowWithRecoverStrategy(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Override
  public WorkflowSettings settings() {
    return WorkflowSettings.builder()
        .defaultStepTimeout(ofSeconds(10))
        .stepPolicy(
            WorkflowWithRecoverStrategy::counterStep,
            maxRetries(1).failoverTo(WorkflowWithRecoverStrategy::counterStepFailover))
        .build();
  }

  public Effect<Message> startFailingCounter(String counterId) {
    return effects()
        .updateState(new FailingCounterState(counterId, 0, false))
        .transitionTo(WorkflowWithRecoverStrategy::counterStep)
        .thenReply(new Message("workflow started"));
  }

  private StepEffect counterStep() {
    var nextValue = currentState().value() + 1;
    componentClient
        .forEventSourcedEntity(currentState().counterId())
        .method(FailingCounterEntity::increase)
        .invoke(nextValue);

    return stepEffects().updateState(currentState().asFinished()).thenEnd();
  }

  private StepEffect counterStepFailover() {
    return stepEffects()
        .updateState(currentState().inc())
        .thenTransitionTo(WorkflowWithRecoverStrategy::counterStep);
  }

  public Effect<FailingCounterState> get() {
    if (currentState() != null) {
      return effects().reply(currentState());
    } else {
      return effects().error("transfer not started");
    }
  }
}
