/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.a2a;

import akka.Done;
import akka.javasdk.a2a.A2aClient;
import akka.javasdk.a2a.A2aMessage;
import akka.javasdk.a2a.A2aSendMessageResponse;
import akka.javasdk.a2a.A2aTaskStatusUpdate;
import akka.javasdk.a2a.A2aTaskUpdate;
import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.workflow.Workflow;
import akka.javasdk.workflow.WorkflowContext;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Sends a message from a step and keeps the step running for a while after the send, so that the
 * updates of the remote task reach {@link #onUpdate} while the step is still running.
 */
@Component(id = "remote-task-during-step-workflow")
public class RemoteTaskDuringStepWorkflow extends Workflow<RemoteTaskDuringStepWorkflow.State> {

  /**
   * @param agent the name of the remote agent in configuration
   * @param holdMillis how long the step runs on after the send
   */
  public record Start(String agent, String skill, String text, long holdMillis) {}

  /**
   * @param stepDone true once the sending step has ended
   * @param updates each update received, with whether the step had ended when it arrived
   * @param outcome {@code completed} when the task ended, else null
   */
  public record State(Start start, boolean stepDone, List<String> updates, String outcome) {}

  private final A2aClient a2aClient;
  private final ComponentClient componentClient;
  private final WorkflowContext context;

  public RemoteTaskDuringStepWorkflow(
      A2aClient a2aClient, ComponentClient componentClient, WorkflowContext context) {
    this.a2aClient = a2aClient;
    this.componentClient = componentClient;
    this.context = context;
  }

  public Effect<Done> start(Start start) {
    return effects()
        .updateState(new State(start, false, List.of(), null))
        .transitionTo(RemoteTaskDuringStepWorkflow::send)
        .thenReply(Done.getInstance());
  }

  private StepEffect send() {
    var start = currentState().start();
    var reply =
        a2aClient
            .forAgent(start.agent())
            .send(start.skill(), A2aMessage.user(start.text()))
            .notifyWorkflow(
                componentClient
                    .forWorkflow(context.workflowId())
                    .method(RemoteTaskDuringStepWorkflow::onUpdate))
            .invoke();
    if (!(reply instanceof A2aSendMessageResponse.Task)) throw new IllegalStateException("no task");
    try {
      Thread.sleep(start.holdMillis());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
    var state = currentState();
    return stepEffects()
        .updateState(new State(start, true, state.updates(), state.outcome()))
        .thenPause(
            pauseSetting(Duration.ofSeconds(30))
                .timeoutHandler(RemoteTaskDuringStepWorkflow::onTimeout));
  }

  public Effect<Done> onUpdate(A2aTaskUpdate update) {
    var state = currentState();
    var updates = new ArrayList<>(state.updates());
    var name =
        update instanceof A2aTaskStatusUpdate status
            ? "status " + status.status().state().wireName()
            : "artifact";
    updates.add(name + (state.stepDone() ? " after the step" : " during the step"));
    var ended = update instanceof A2aTaskStatusUpdate status && status.status().state().terminal();
    var updated = new State(state.start(), state.stepDone(), updates, ended ? "completed" : null);
    if (ended) return effects().updateState(updated).end().thenReply(Done.getInstance());
    return effects().updateState(updated).pause().thenReply(Done.getInstance());
  }

  public Effect<Done> onTimeout() {
    var state = currentState();
    return effects()
        .updateState(new State(state.start(), state.stepDone(), state.updates(), "timed out"))
        .end()
        .thenReply(Done.getInstance());
  }

  public ReadOnlyEffect<State> get() {
    return effects().reply(currentState());
  }
}
