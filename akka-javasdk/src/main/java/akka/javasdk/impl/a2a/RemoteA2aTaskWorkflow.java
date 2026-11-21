/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a;

import akka.Done;
import akka.annotation.InternalApi;
import akka.javasdk.a2a.A2aTaskState;
import akka.javasdk.a2a.A2aTaskStatusUpdate;
import akka.javasdk.a2a.A2aTaskUpdate;
import akka.javasdk.agent.SessionMemoryEntity;
import akka.javasdk.agent.SessionMessage;
import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.workflow.Workflow;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * INTERNAL API
 *
 * <p>Follows a remote A2A task that an agent's tool call handed off, because the task was still
 * working after the hand-off time. The workflow receives the task's updates like any workflow that
 * a {@code notifyWorkflow} names. When the task ends or needs input, it reads the task and adds its
 * outcome to the session memory of the agent, as a user message, so the model reads it with the
 * next request in the session.
 */
@InternalApi
@Component(id = "akka-a2a-remote-task-hand-off")
public final class RemoteA2aTaskWorkflow extends Workflow<RemoteA2aTaskWorkflow.State> {

  private static final Logger log = LoggerFactory.getLogger(RemoteA2aTaskWorkflow.class);

  static final Duration READ_TIMEOUT = Duration.ofMinutes(1);

  /**
   * @param sessionId the session of the agent, which is also the A2A context id
   * @param agentComponentId the component id of the agent
   * @param toolName the name of the tool that handed off the task
   * @param remote the remote agent
   * @param taskId the id of the remote task
   * @param lastStatus the last status update received, or null
   * @param recorded true once the outcome is in session memory
   */
  public record State(
      String sessionId,
      String agentComponentId,
      String toolName,
      A2aRemoteRef remote,
      String taskId,
      A2aTaskStatusUpdate lastStatus,
      boolean recorded) {
    State withLastStatus(A2aTaskStatusUpdate status) {
      return new State(sessionId, agentComponentId, toolName, remote, taskId, status, recorded);
    }

    State asRecorded() {
      return new State(sessionId, agentComponentId, toolName, remote, taskId, lastStatus, true);
    }
  }

  private final A2aRemoteTasks remoteTasks;
  private final ComponentClient componentClient;

  public RemoteA2aTaskWorkflow(A2aRemoteTasks remoteTasks, ComponentClient componentClient) {
    this.remoteTasks = remoteTasks;
    this.componentClient = componentClient;
  }

  @Override
  public WorkflowSettings settings() {
    return WorkflowSettings.builder().defaultStepTimeout(READ_TIMEOUT.plusSeconds(10)).build();
  }

  /** Starts following the task. A workflow that has started is left as it is. */
  public Effect<Done> start(State handOff) {
    if (currentState() != null) return effects().reply(Done.getInstance());
    return effects().updateState(handOff).pause().thenReply(Done.getInstance());
  }

  /** Receives the updates of the remote task. */
  public Effect<Done> onUpdate(A2aTaskUpdate update) {
    var state = currentState();
    if (state == null) return effects().error("No remote task is handed off");
    if (state.recorded()) return effects().reply(Done.getInstance());
    if (update instanceof A2aTaskStatusUpdate status && settles(status)) {
      return effects()
          .updateState(state.withLastStatus(status))
          .transitionTo(RemoteA2aTaskWorkflow::record)
          .thenReply(Done.getInstance());
    }
    return effects().reply(Done.getInstance());
  }

  private static boolean settles(A2aTaskStatusUpdate update) {
    var state = update.status() == null ? null : update.status().state();
    return state != null
        && (state.terminal()
            || state == A2aTaskState.INPUT_REQUIRED
            || state == A2aTaskState.AUTH_REQUIRED);
  }

  private StepEffect record() {
    var state = currentState();
    String outcome;
    try {
      outcome =
          remoteTasks
              .handOffOutcome(state.remote(), state.taskId())
              .toCompletableFuture()
              .get(READ_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    } catch (Exception e) {
      log.debug("Reading remote A2A task [{}] failed", state.taskId(), e);
      outcome = A2aRemoteTasks.statusOutcome(state.lastStatus());
    }
    var text =
        "The remote task ["
            + state.taskId()
            + "] that the tool ["
            + state.toolName()
            + "] handed off has an outcome:\n"
            + outcome;
    var message = new SessionMessage.UserMessage(Instant.now(), text, state.agentComponentId());
    componentClient
        .forEventSourcedEntity(state.sessionId())
        .method(SessionMemoryEntity::addInteraction)
        .invoke(new SessionMemoryEntity.AddInteractionCmd(message, List.of()));
    return stepEffects().updateState(state.asRecorded()).thenEnd();
  }
}
