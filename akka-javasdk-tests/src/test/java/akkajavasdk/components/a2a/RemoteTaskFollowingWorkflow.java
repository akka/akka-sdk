/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.a2a;

import akka.Done;
import akka.javasdk.a2a.A2aClient;
import akka.javasdk.a2a.A2aMessage;
import akka.javasdk.a2a.A2aPart;
import akka.javasdk.a2a.A2aSendMessageResponse;
import akka.javasdk.a2a.A2aTaskArtifactUpdate;
import akka.javasdk.a2a.A2aTaskStatusUpdate;
import akka.javasdk.a2a.A2aTaskUpdate;
import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.workflow.Workflow;
import akka.javasdk.workflow.WorkflowContext;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Sends a message to a remote A2A agent from a step, pauses, and resumes on the updates of the
 * remote task, which {@code notifyWorkflow} delivers to {@link #onUpdate}.
 */
@Component(id = "remote-task-following-workflow")
public class RemoteTaskFollowingWorkflow extends Workflow<RemoteTaskFollowingWorkflow.State> {

  /**
   * @param agent the name of the remote agent in configuration
   * @param text the message to send
   * @param timeoutMillis how long to wait for the remote task
   */
  public record Start(String agent, String text, long timeoutMillis) {}

  /**
   * @param taskId the id of the remote task, once sent
   * @param updates the updates received, as text
   * @param outcome {@code completed}, {@code timed out}, or null while waiting
   */
  public record State(Start start, String taskId, List<String> updates, String outcome) {
    State with(String taskId, List<String> updates, String outcome) {
      return new State(start, taskId, updates, outcome);
    }
  }

  private final A2aClient a2aClient;
  private final ComponentClient componentClient;
  private final WorkflowContext context;

  public RemoteTaskFollowingWorkflow(
      A2aClient a2aClient, ComponentClient componentClient, WorkflowContext context) {
    this.a2aClient = a2aClient;
    this.componentClient = componentClient;
    this.context = context;
  }

  public Effect<Done> start(Start start) {
    return effects()
        .updateState(new State(start, null, List.of(), null))
        .transitionTo(RemoteTaskFollowingWorkflow::send)
        .thenReply(Done.getInstance());
  }

  private StepEffect send() {
    var start = currentState().start();
    var reply =
        a2aClient
            .forAgent(start.agent())
            .send("assess", A2aMessage.user(start.text()))
            .notifyWorkflow(
                componentClient
                    .forWorkflow(context.workflowId())
                    .method(RemoteTaskFollowingWorkflow::onUpdate))
            .invoke();
    var taskId = ((A2aSendMessageResponse.Task) reply).task().id();
    return stepEffects()
        .updateState(currentState().with(taskId, currentState().updates(), null))
        .thenPause(
            pauseSetting(Duration.ofMillis(start.timeoutMillis()))
                .timeoutHandler(RemoteTaskFollowingWorkflow::onTimeout));
  }

  public Effect<Done> onUpdate(A2aTaskUpdate update) {
    var state = currentState();
    var updates = new ArrayList<>(state.updates());
    updates.add(describe(update));
    var ended = update instanceof A2aTaskStatusUpdate status && status.status().state().terminal();
    if (ended) {
      return effects()
          .updateState(state.with(state.taskId(), updates, "completed"))
          .end()
          .thenReply(Done.getInstance());
    }
    return effects()
        .updateState(state.with(state.taskId(), updates, null))
        .pause()
        .thenReply(Done.getInstance());
  }

  public Effect<Done> onTimeout() {
    var state = currentState();
    return effects()
        .updateState(state.with(state.taskId(), state.updates(), "timed out"))
        .end()
        .thenReply(Done.getInstance());
  }

  public ReadOnlyEffect<State> get() {
    return effects().reply(currentState());
  }

  private static String describe(A2aTaskUpdate update) {
    return switch (update) {
      case A2aTaskStatusUpdate status -> "status " + status.status().state().wireName();
      case A2aTaskArtifactUpdate artifact ->
          "artifact "
              + artifact.artifact().parts().stream()
                  .map(p -> p instanceof A2aPart.Text t ? t.text() : "")
                  .collect(Collectors.joining());
    };
  }
}
