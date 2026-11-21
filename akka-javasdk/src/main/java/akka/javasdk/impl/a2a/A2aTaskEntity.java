/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a;

import akka.annotation.InternalApi;
import akka.javasdk.NotificationPublisher;
import akka.javasdk.a2a.A2aTask;
import akka.javasdk.a2a.A2aTaskState;
import akka.javasdk.a2a.A2aTaskStatus;
import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import java.time.Instant;

/**
 * INTERNAL API
 *
 * <p>Stores one A2A task, keyed by the task id: its status, history and artifacts, the skill that
 * answers it, the subject that created it, and a version that each change increases. Each change is
 * published, so that streams can follow the task.
 */
@InternalApi
@Component(id = "akka-a2a-task")
public final class A2aTaskEntity extends KeyValueEntity<A2aTaskEntity.State> {

  /**
   * @param task the task as A2A clients see it
   * @param skillId the skill that answers the task
   * @param owner the JWT subject of the caller that created the task, or null without a token
   * @param version increases by one with each change, starting at 1
   */
  public record State(A2aTask task, String skillId, String owner, long version) {}

  /** The command that stores a new task. */
  public record Create(A2aTask task, String skillId, String owner) {}

  /** The reply of {@link #get()}. The state is null when the task does not exist. */
  public record Lookup(State state) {}

  /** The reply of {@link #cancel()}: the state after the call, and whether the call canceled it. */
  public record CancelResult(State state, boolean canceled) {}

  /** Published with each change of the task. */
  public record Changed(A2aTask task, long version) {}

  private final NotificationPublisher<Changed> notificationPublisher;

  public A2aTaskEntity(NotificationPublisher<Changed> notificationPublisher) {
    this.notificationPublisher = notificationPublisher;
  }

  public Effect<State> create(Create command) {
    if (currentState() != null) {
      return effects().error("Task [" + commandContext().entityId() + "] already exists");
    }
    return store(new State(command.task(), command.skillId(), command.owner(), 1));
  }

  public Effect<State> update(A2aTask task) {
    var current = currentState();
    if (current == null) {
      return effects().error("Task [" + commandContext().entityId() + "] does not exist");
    }
    // A cancel wins over the outcome of a call that was still running when the task was canceled.
    if (current.task().status().state() == A2aTaskState.CANCELED) {
      return effects().reply(current);
    }
    if (current.task().status().state().terminal()) {
      return effects().error("Task [" + commandContext().entityId() + "] has already ended");
    }
    return store(new State(task, current.skillId(), current.owner(), current.version() + 1));
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
    var task = current.task();
    var canceled =
        new State(
            new A2aTask(
                task.id(),
                task.contextId(),
                new A2aTaskStatus(A2aTaskState.CANCELED, null, Instant.now()),
                task.artifacts(),
                task.history(),
                task.metadata()),
            current.skillId(),
            current.owner(),
            current.version() + 1);
    return effects()
        .updateState(canceled)
        .thenReply(
            () -> {
              notificationPublisher.publish(new Changed(canceled.task(), canceled.version()));
              return new CancelResult(canceled, true);
            });
  }

  public ReadOnlyEffect<Lookup> get() {
    return effects().reply(new Lookup(currentState()));
  }

  public NotificationPublisher.NotificationStream<Changed> updates() {
    return notificationPublisher.stream();
  }

  private Effect<State> store(State state) {
    return effects()
        .updateState(state)
        .thenReply(
            () -> {
              notificationPublisher.publish(new Changed(state.task(), state.version()));
              return state;
            });
  }
}
