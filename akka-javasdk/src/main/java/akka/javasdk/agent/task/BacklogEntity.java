/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.task;

import static akka.Done.done;

import akka.Done;
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

  public BacklogEntity(EventSourcedEntityContext context) {}

  @Override
  public BacklogState emptyState() {
    return BacklogState.empty();
  }

  /** Create this backlog with a name. */
  public Effect<Done> create(String name) {
    if (currentState().isCreated()) {
      return effects().reply(done()); // idempotent
    }
    return effects().persist(new BacklogEvent.BacklogCreated(name)).thenReply(__ -> done());
  }

  /** Add a task ID reference to this backlog. The task must already exist in TaskEntity. */
  public Effect<Done> addTask(String taskId) {
    if (currentState().containsTask(taskId)) {
      return effects().reply(done()); // idempotent
    }
    return effects().persist(new BacklogEvent.TaskAdded(taskId)).thenReply(__ -> done());
  }

  /** Atomic first-come-first-served claim. */
  public Effect<Done> claim(ClaimRequest request) {
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
        .thenReply(__ -> done());
  }

  /** Release a claimed task back to unclaimed. */
  public Effect<Done> release(String taskId) {
    if (!currentState().containsTask(taskId)) {
      return effects().error("Task " + taskId + " is not in this backlog");
    }
    if (!currentState().isClaimed(taskId)) {
      return effects().reply(done()); // already unclaimed, idempotent
    }
    return effects().persist(new BacklogEvent.TaskReleased(taskId)).thenReply(__ -> done());
  }

  /** Transfer a claimed task directly to a different agent. */
  public Effect<Done> transfer(TransferRequest request) {
    if (!currentState().containsTask(request.taskId())) {
      return effects().error("Task " + request.taskId() + " is not in this backlog");
    }
    return effects()
        .persist(new BacklogEvent.TaskTransferred(request.taskId(), request.transferredTo()))
        .thenReply(__ -> done());
  }

  /** Remove all unclaimed tasks from the backlog. */
  public Effect<Done> cancelUnclaimed() {
    if (currentState().unclaimedTaskIds().isEmpty()) {
      return effects().reply(done()); // nothing to cancel
    }
    return effects().persist(new BacklogEvent.UnclaimedCancelled()).thenReply(__ -> done());
  }

  /** Get the current state of the backlog. */
  public ReadOnlyEffect<BacklogState> getState() {
    return effects().reply(currentState());
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
    };
  }
}
