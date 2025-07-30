/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.workflowentities;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.workflow.Workflow;
import akka.javasdk.workflow.WorkflowContext;
import akkajavasdk.components.actions.echo.Message;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

@ComponentId("workflow-with-timer")
public class WorkflowWithTimer extends Workflow<FailingCounterState> {

  private final String counterStepName = "counter";

  private final WorkflowContext workflowContext;
  private final ComponentClient componentClient;

  public WorkflowWithTimer(WorkflowContext workflowContext, ComponentClient componentClient) {
    this.workflowContext = workflowContext;
    this.componentClient = componentClient;
  }

  @Override
  public WorkflowDef<FailingCounterState> definition() {
    var counterInc =
        step(counterStepName)
            .asyncCall(
                () -> {
                  var pingWorkflow =
                      componentClient
                          .forWorkflow(workflowContext.workflowId())
                          .method(WorkflowWithTimer::pingWorkflow)
                          .deferred(new CounterScheduledValue(12));

                  timers().createSingleTimer("ping", Duration.ofSeconds(2), pingWorkflow);

                  return CompletableFuture.completedFuture(
                      Done.done()); // FIXME remove once we have sync/blocking workflow calls
                })
            .andThen(Done.class, __ -> effects().pause())
            .timeout(Duration.ofMillis(50));

    return workflow().addStep(counterInc);
  }

  public Effect<Message> startFailingCounter(String counterId) {
    return effects()
        .updateState(new FailingCounterState(counterId, 0, false))
        .transitionTo(counterStepName)
        .thenReply(new Message("workflow started"));
  }

  public Effect<Message> startFailingCounterWithReqParam(String counterId) {
    return effects()
        .updateState(new FailingCounterState(counterId, 0, false))
        .transitionTo(counterStepName)
        .thenReply(new Message("workflow started"));
  }

  public Effect<String> pingWorkflow(CounterScheduledValue counterScheduledValue) {
    return effects()
        .updateState(currentState().asFinished(counterScheduledValue.value()))
        .end()
        .thenReply("workflow finished");
  }

  public Effect<FailingCounterState> get() {
    return effects().reply(currentState());
  }
}
