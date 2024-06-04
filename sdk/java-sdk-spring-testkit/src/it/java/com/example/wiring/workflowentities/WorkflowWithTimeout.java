/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.workflowentities;

import com.example.wiring.actions.echo.Message;
import kalix.javasdk.annotations.TypeId;
import kalix.javasdk.client.ComponentClient;
import kalix.javasdk.workflow.Workflow;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;

@TypeId("workflow-with-timeout")
public class WorkflowWithTimeout extends Workflow<FailingCounterState> {

  private final String counterStepName = "counter";
  private final String counterFailoverStepName = "counter-failover";

  private ComponentClient componentClient;

  public WorkflowWithTimeout(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }


  public Executor delayedExecutor = CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS);

  @Override
  public WorkflowDef<FailingCounterState> definition() {
    var counterInc =
      step(counterStepName)
        .asyncCall(() -> CompletableFuture.supplyAsync(() -> "nothing", delayedExecutor))
        .andThen(String.class, __ -> effects().end())
        .timeout(Duration.ofMillis(50));

    var counterIncFailover =
      step(counterFailoverStepName)
        .call(Integer.class, value -> componentClient.forEventSourcedEntity(currentState().counterId()).methodRef(FailingCounterEntity::increase).deferred(value))
        .andThen(Integer.class, __ ->
          effects()
            .updateState(currentState().asFinished())
            .transitionTo(counterStepName)
        );


    return workflow()
      .timeout(ofSeconds(1))
      .defaultStepTimeout(ofMillis(999))
      .failoverTo(counterFailoverStepName, 3, maxRetries(1))
      .addStep(counterInc, maxRetries(1).failoverTo(counterStepName))
      .addStep(counterIncFailover);
  }

  public Effect<Message> startFailingCounter(String counterId) {
    return effects()
      .updateState(new FailingCounterState(counterId, 0, false))
      .transitionTo(counterStepName)
      .thenReply(new Message("workflow started"));
  }

  public Effect<FailingCounterState> get() {
    return effects().reply(currentState());
  }
}
