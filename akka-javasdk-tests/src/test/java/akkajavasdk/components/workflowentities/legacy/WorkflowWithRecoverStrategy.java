/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.workflowentities.legacy;

import static java.time.Duration.ofSeconds;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.workflow.Workflow;
import akkajavasdk.components.actions.echo.Message;
import akkajavasdk.components.workflowentities.FailingCounterEntity;
import akkajavasdk.components.workflowentities.FailingCounterState;

@ComponentId("workflow-with-recover-strategy-legacy")
public class WorkflowWithRecoverStrategy extends Workflow<FailingCounterState> {

  private final String counterStepName = "counter";
  private final String counterFailoverStepName = "counter-failover";

  private ComponentClient componentClient;

  public WorkflowWithRecoverStrategy(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }


  @Override
  public WorkflowDef<FailingCounterState> definition() {
    var counterInc =
        step(counterStepName)
            .call(() -> {
              var nextValue = currentState().value() + 1;
              return componentClient
                  .forEventSourcedEntity(currentState().counterId())
                  .method(FailingCounterEntity::increase)
                  .invoke(nextValue);
            })
            .andThen(Integer.class, __ -> effects()
                .updateState(currentState().asFinished())
                .end());

    var counterIncFailover =
        step(counterFailoverStepName)
            .call(() -> "nothing")
            .andThen(String.class, __ ->
                effects()
                    .updateState(currentState().inc())
                    .transitionTo(counterStepName)
            );


    return workflow()
        .timeout(ofSeconds(30))
        .defaultStepTimeout(ofSeconds(10))
        .addStep(counterInc, maxRetries(1).failoverTo(counterFailoverStepName))
        .addStep(counterIncFailover);
  }

  public Effect<Message> startFailingCounter(String counterId) {
    return effects()
        .updateState(new FailingCounterState(counterId, 0, false))
        .transitionTo(counterStepName)
        .thenReply(new Message("workflow started"));
  }

  public Effect<FailingCounterState> get(){
    if (currentState() != null) {
      return effects().reply(currentState());
    } else {
      return effects().error("transfer not started");
    }
  }
}
