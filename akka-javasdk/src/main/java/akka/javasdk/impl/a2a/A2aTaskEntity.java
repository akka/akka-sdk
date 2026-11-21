/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a;

import akka.annotation.InternalApi;
import akka.javasdk.NotificationPublisher;
import akka.javasdk.a2a.A2aArtifact;
import akka.javasdk.a2a.A2aMessage;
import akka.javasdk.a2a.A2aRole;
import akka.javasdk.a2a.A2aTask;
import akka.javasdk.a2a.A2aTaskState;
import akka.javasdk.a2a.A2aTaskStatus;
import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import java.time.Instant;
import java.util.ArrayList;

/**
 * INTERNAL API
 *
 * <p>Stores one A2A task, keyed by the task id: its status, history and artifacts, the skill that
 * answers it, the subject that created it, and a version that each change increases. Each change is
 * published, so that streams can follow the task.
 *
 * <p>Every change is a command that the entity applies to its own state, so writers on several
 * nodes cannot overwrite each other's changes.
 */
@InternalApi
@Component(id = "akka-a2a-task")
public final class A2aTaskEntity extends KeyValueEntity<A2aTaskEntity.State> {

  /**
   * @param task the task as A2A clients see it
   * @param skillId the skill that answers the task
   * @param owner the JWT subject of the caller that created the task, or null without a token
   * @param version increases by one with each change, starting at 1
   * @param sessionId the agent session of the caller and the context ({@link A2aSessions})
   */
  public record State(A2aTask task, String skillId, String owner, long version, String sessionId) {}

  /** The command that stores a new task. */
  public record Create(A2aTask task, String skillId, String owner, String sessionId) {}

  /**
   * A change of a task that has not ended. A null member leaves that part of the task as it is.
   *
   * @param historyMessage a message to add to the history. A message with an id that the history
   *     has, or an agent message with the same parts as the latest message, is not added again.
   * @param artifact an artifact to add, or to replace the one with the same id
   * @param state the new state of the status
   * @param statusMessage the message of the new status; used only with {@code state}
   * @param resumeWhenInputRequired true to move a task that waits for input to working, when the
   *     change sets no state
   */
  public record Change(
      A2aMessage historyMessage,
      A2aArtifact artifact,
      A2aTaskState state,
      A2aMessage statusMessage,
      boolean resumeWhenInputRequired) {

    public static Change status(A2aTaskState state, A2aMessage statusMessage) {
      return new Change(null, null, state, statusMessage, false);
    }

    public static Change artifact(A2aArtifact artifact) {
      return new Change(null, artifact, null, null, false);
    }
  }

  /** The reply of {@link #change}: the state after the command, and whether it changed it. */
  public record Changed(State state, boolean applied) {}

  /** The reply of {@link #get()}. The state is null when the task does not exist. */
  public record Lookup(State state) {}

  /** The reply of {@link #cancel()}: the state after the call, and whether the call canceled it. */
  public record CancelResult(State state, boolean canceled) {}

  /** Published with each change of the task. */
  public record Notification(A2aTask task, long version) {}

  private final NotificationPublisher<Notification> notificationPublisher;

  public A2aTaskEntity(NotificationPublisher<Notification> notificationPublisher) {
    this.notificationPublisher = notificationPublisher;
  }

  public Effect<State> create(Create command) {
    if (currentState() != null) {
      return effects().error("Task [" + commandContext().entityId() + "] already exists");
    }
    var state =
        new State(command.task(), command.skillId(), command.owner(), 1, command.sessionId());
    return effects().updateState(state).thenReply(() -> published(state));
  }

  /**
   * Applies a change to the task. A task that has ended is left as it is, so a cancel wins over the
   * outcome of a call that was still running when the task was canceled.
   */
  public Effect<Changed> change(Change change) {
    var current = currentState();
    if (current == null) {
      return effects().error("Task [" + commandContext().entityId() + "] does not exist");
    }
    if (current.task().status().state().terminal()) {
      return effects().reply(new Changed(current, false));
    }
    var task = current.task();
    var resume =
        change.resumeWhenInputRequired()
            && change.state() == null
            && task.status().state() == A2aTaskState.INPUT_REQUIRED;
    if (change.historyMessage() != null) task = withHistory(task, change.historyMessage());
    if (change.artifact() != null) task = withArtifact(task, change.artifact());
    if (change.state() != null) task = withStatus(task, change.state(), change.statusMessage());
    else if (resume) task = withStatus(task, A2aTaskState.WORKING, null);
    var changed =
        new State(
            task, current.skillId(), current.owner(), current.version() + 1, current.sessionId());
    return effects().updateState(changed).thenReply(() -> new Changed(published(changed), true));
  }

  /**
   * Cancels the task when it has not ended. The reply says whether this call canceled it, and
   * carries the state either way.
   */
  public Effect<CancelResult> cancel() {
    var current = currentState();
    if (current == null) {
      return effects().error("Task [" + commandContext().entityId() + "] does not exist");
    }
    if (current.task().status().state().terminal()) {
      return effects().reply(new CancelResult(current, false));
    }
    var canceled =
        new State(
            withStatus(current.task(), A2aTaskState.CANCELED, null),
            current.skillId(),
            current.owner(),
            current.version() + 1,
            current.sessionId());
    return effects()
        .updateState(canceled)
        .thenReply(() -> new CancelResult(published(canceled), true));
  }

  public ReadOnlyEffect<Lookup> get() {
    return effects().reply(new Lookup(currentState()));
  }

  public NotificationPublisher.NotificationStream<Notification> updates() {
    return notificationPublisher.stream();
  }

  private State published(State state) {
    notificationPublisher.publish(new Notification(state.task(), state.version()));
    return state;
  }

  // ---------------------------------------------------------------------------------------------
  // Changes of a task

  private static A2aTask withStatus(A2aTask task, A2aTaskState state, A2aMessage message) {
    return new A2aTask(
        task.id(),
        task.contextId(),
        new A2aTaskStatus(state, message, Instant.now()),
        task.artifacts(),
        task.history(),
        task.metadata());
  }

  private static A2aTask withHistory(A2aTask task, A2aMessage message) {
    var history = new ArrayList<>(task.history());
    boolean known = history.stream().anyMatch(m -> m.messageId().equals(message.messageId()));
    boolean repeated =
        message.role() == A2aRole.AGENT
            && !history.isEmpty()
            && history.getLast().role() == A2aRole.AGENT
            && history.getLast().parts().equals(message.parts());
    if (known || repeated) return task;
    history.add(message);
    return new A2aTask(
        task.id(), task.contextId(), task.status(), task.artifacts(), history, task.metadata());
  }

  private static A2aTask withArtifact(A2aTask task, A2aArtifact artifact) {
    var artifacts = new ArrayList<>(task.artifacts());
    int index = -1;
    for (int i = 0; i < artifacts.size(); i++) {
      if (artifacts.get(i).artifactId().equals(artifact.artifactId())) index = i;
    }
    if (index >= 0) artifacts.set(index, artifact);
    else artifacts.add(artifact);
    return new A2aTask(
        task.id(), task.contextId(), task.status(), artifacts, task.history(), task.metadata());
  }
}
