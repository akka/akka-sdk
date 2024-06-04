/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.workflowentities;

import com.example.wiring.actions.echo.Message;
import kalix.javasdk.annotations.TypeId;
import kalix.javasdk.client.ComponentClient;
import kalix.javasdk.workflow.Workflow;

import java.util.concurrent.CompletableFuture;

import static java.time.Duration.ofSeconds;

@TypeId("workflow-with-recover-strategy")
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
                  .methodRef(FailingCounterEntity::increase)
                  .deferred(nextValue);
            })
            .andThen(Integer.class, __ -> effects()
                .updateState(currentState().asFinished())
                .end());

    var counterIncFailover =
        step(counterFailoverStepName)
            .asyncCall(() -> CompletableFuture.completedStage("nothing"))
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
