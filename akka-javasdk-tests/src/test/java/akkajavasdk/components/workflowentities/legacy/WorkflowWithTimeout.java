/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.workflowentities.legacy;

import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.workflow.Workflow;
import akkajavasdk.components.actions.echo.Message;
import akkajavasdk.components.workflowentities.FailingCounterEntity;
import akkajavasdk.components.workflowentities.FailingCounterState;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@ComponentId("workflow-with-timeout-legacy")
public class WorkflowWithTimeout extends Workflow<FailingCounterState> {

  private final String counterStepName = "counter";
  private final String counterFailoverStepName = "counter-failover";

  private final ComponentClient componentClient;

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
        .call(Integer.class, value -> componentClient.forEventSourcedEntity(currentState().counterId()).method(FailingCounterEntity::increase).invoke(value))
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
