/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.task;

import static akka.Done.done;

import akka.Done;
import akka.javasdk.NotificationPublisher;
import akka.javasdk.annotations.Component;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;

/**
 * Manages a shared backlog of task references with atomic claiming semantics. Multiple agents can
 * browse, claim, release, and transfer tasks concurrently.
 */
@Component(id = "akka-backlog")
public final class BacklogEntity extends EventSourcedEntity<BacklogState, BacklogEvent> {

  public record ClaimRequest(String taskId, String claimedBy) {}

  public record TransferRequest(String taskId, String transferredTo) {}

  private final NotificationPublisher<BacklogNotification> notificationPublisher;

  public BacklogEntity(
      EventSourcedEntityContext context,
      NotificationPublisher<BacklogNotification> notificationPublisher) {
    this.notificationPublisher = notificationPublisher;
  }

  @Override
  public BacklogState emptyState() {
    return BacklogState.empty();
  }

  /** Create this backlog with a name. */
  public Effect<Done> create(String name) {
    if (currentState().closed()) {
      return closedError();
    }
    if (currentState().isCreated()) {
      return effects().reply(done()); // idempotent
    }
    return effects()
        .persist(new BacklogEvent.BacklogCreated(name))
        .thenReply(
            __ -> {
              notificationPublisher.publish(new BacklogNotification.BacklogCreated(name));
              return done();
            });
  }

  /** Add a task reference to this backlog. The task must already exist in TaskEntity. */
  public Effect<Done> addTask(String taskId) {
    if (currentState().closed()) {
      return closedError();
    }
    if (currentState().containsTask(taskId)) {
      return effects().reply(done()); // idempotent
    }
    return effects()
        .persist(new BacklogEvent.TaskAdded(taskId))
        .thenReply(
            __ -> {
              notificationPublisher.publish(new BacklogNotification.TaskAdded(taskId));
              return done();
            });
  }

  /** Atomic first-come-first-served claim. */
  public Effect<Done> claim(ClaimRequest request) {
    if (currentState().closed()) {
      return closedError();
    }
    if (!currentState().containsTask(request.taskId())) {
      return effects().error("Task " + request.taskId() + " is not in this backlog");
    }
    if (currentState().isClaimed(request.taskId())) {
      var currentClaimant = currentState().claimedBy(request.taskId()).orElse("unknown");
      return effects()
          .error("Task " + request.taskId() + " is already claimed by " + currentClaimant);
    }
    return effects()
        .persist(new BacklogEvent.TaskClaimed(request.taskId(), request.claimedBy()))
        .thenReply(
            __ -> {
              notificationPublisher.publish(
                  new BacklogNotification.TaskClaimed(request.taskId(), request.claimedBy()));
              return done();
            });
  }

  /** Release a claimed task back to unclaimed. */
  public Effect<Done> release(String taskId) {
    if (currentState().closed()) {
      return closedError();
    }
    if (!currentState().containsTask(taskId)) {
      return effects().error("Task " + taskId + " is not in this backlog");
    }
    if (!currentState().isClaimed(taskId)) {
      return effects().reply(done()); // already unclaimed, idempotent
    }
    return effects()
        .persist(new BacklogEvent.TaskReleased(taskId))
        .thenReply(
            __ -> {
              notificationPublisher.publish(new BacklogNotification.TaskReleased(taskId));
              return done();
            });
  }

  /** Transfer a claimed task directly to a different agent. */
  public Effect<Done> transfer(TransferRequest request) {
    if (currentState().closed()) {
      return closedError();
    }
    if (!currentState().containsTask(request.taskId())) {
      return effects().error("Task " + request.taskId() + " is not in this backlog");
    }
    return effects()
        .persist(new BacklogEvent.TaskTransferred(request.taskId(), request.transferredTo()))
        .thenReply(
            __ -> {
              notificationPublisher.publish(
                  new BacklogNotification.TaskTransferred(
                      request.taskId(), request.transferredTo()));
              return done();
            });
  }

  /** Remove all unclaimed tasks from the backlog. */
  public Effect<Done> cancelUnclaimed() {
    if (currentState().closed()) {
      return closedError();
    }
    if (currentState().unclaimedTaskIds().isEmpty()) {
      return effects().reply(done()); // nothing to cancel
    }
    return effects()
        .persist(new BacklogEvent.UnclaimedCancelled())
        .thenReply(
            __ -> {
              notificationPublisher.publish(new BacklogNotification.UnclaimedCancelled());
              return done();
            });
  }

  /** Close the backlog — no further modifications allowed. */
  public Effect<Done> close() {
    if (currentState().closed()) {
      return effects().reply(done()); // idempotent
    }
    return effects()
        .persist(new BacklogEvent.BacklogClosed())
        .thenReply(
            __ -> {
              notificationPublisher.publish(new BacklogNotification.BacklogClosed());
              return done();
            });
  }

  /** Get the current state of the backlog. */
  public ReadOnlyEffect<BacklogState> getState() {
    return effects().reply(currentState());
  }

  public NotificationPublisher.NotificationStream<BacklogNotification> notifications() {
    return notificationPublisher.stream();
  }

  private Effect<Done> closedError() {
    return effects().error("Backlog is closed");
  }

  @Override
  public BacklogState applyEvent(BacklogEvent event) {
    return switch (event) {
      case BacklogEvent.BacklogCreated e -> currentState().withName(e.name());
      case BacklogEvent.TaskAdded e -> currentState().withTaskAdded(e.taskId());
      case BacklogEvent.TaskClaimed e -> currentState().withTaskClaimed(e.taskId(), e.claimedBy());
      case BacklogEvent.TaskReleased e -> currentState().withTaskReleased(e.taskId());
      case BacklogEvent.TaskTransferred e ->
          currentState().withTaskClaimed(e.taskId(), e.transferredTo());
      case BacklogEvent.UnclaimedCancelled e -> currentState().withUnclaimedRemoved();
      case BacklogEvent.BacklogClosed e -> currentState().withClosed();
    };
  }
}
