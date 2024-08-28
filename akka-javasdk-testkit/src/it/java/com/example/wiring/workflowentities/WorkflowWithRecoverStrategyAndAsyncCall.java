/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.workflowentities;

import com.example.wiring.actions.echo.Message;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.workflow.Workflow;

import java.util.concurrent.CompletableFuture;

import static java.time.Duration.ofSeconds;

@ComponentId("workflow-with-recover-strategy-async")
public class WorkflowWithRecoverStrategyAndAsyncCall extends Workflow<FailingCounterState> {

  private final String counterStepName = "counter";
  private final String counterFailoverStepName = "counter-failover";

  private ComponentClient componentClient;

  public WorkflowWithRecoverStrategyAndAsyncCall(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }


  @Override
  public WorkflowDef<FailingCounterState> definition() {
    var counterInc =
      step(counterStepName)
        .asyncCall(() -> {
          var nextValue = currentState().value() + 1;
          return componentClient
            .forEventSourcedEntity(currentState().counterId())
            .method(FailingCounterEntity::increase)
            .invokeAsync(nextValue);
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

  public Effect<FailingCounterState> get() {
    if (currentState() != null) {
      return effects().reply(currentState());
    } else {
      return effects().error("transfer not started");
    }
  }
}
