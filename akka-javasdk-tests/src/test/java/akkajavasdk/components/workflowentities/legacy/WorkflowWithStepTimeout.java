/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.workflowentities.legacy;

import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;

import akka.javasdk.annotations.Component;
import akka.javasdk.workflow.Workflow;
import akkajavasdk.components.actions.echo.Message;
import akkajavasdk.components.workflowentities.FailingCounterState;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(id = "workflow-with-step-timeout-legacy")
public class WorkflowWithStepTimeout extends Workflow<FailingCounterState> {

  private Logger logger = LoggerFactory.getLogger(getClass());
  private final String counterStepName = "counter";
  private final String counterFailoverStepName = "counter-failover";

  public Executor delayedExecutor = CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS);

  @Override
  public WorkflowDef<FailingCounterState> definition() {
    var counterInc =
        step(counterStepName)
            .asyncCall(
                () -> {
                  logger.info("Running");
                  return CompletableFuture.supplyAsync(() -> "produces time out", delayedExecutor);
                })
            .andThen(String.class, __ -> effects().transitionTo(counterFailoverStepName))
            .timeout(ofMillis(20));

    var counterIncFailover =
        step(counterFailoverStepName)
            .call(() -> "nothing")
            .andThen(
                String.class,
                __ -> {
                  var updatedState = currentState().inc();
                  if (updatedState.value() == 2) {
                    return effects().updateState(updatedState.asFinished()).end();
                  } else {
                    return effects().updateState(updatedState).transitionTo(counterStepName);
                  }
                });

    return workflow()
        .timeout(ofSeconds(8))
        .defaultStepTimeout(ofMillis(20))
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
    return effects().reply(currentState());
  }
}
