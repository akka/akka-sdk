/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.autonomous;

import static akka.Done.done;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;

/** Shared task list entity. Tasks can be added, claimed by agents, and completed independently. */
@Component(id = "akka-task-list")
public final class TaskListEntity extends EventSourcedEntity<TaskListState, TaskListEvent> {

  private final String listId;

  public TaskListEntity(EventSourcedEntityContext context) {
    this.listId = context.entityId();
  }

  @Override
  public TaskListState emptyState() {
    return TaskListState.empty();
  }

  public Effect<Done> create() {
    if (!currentState().listId().isEmpty()) {
      return effects().reply(done()); // idempotent
    }
    return effects().persist(new TaskListEvent.TaskListCreated(listId)).thenReply(__ -> done());
  }

  public record AddTaskRequest(String taskId, String description) {}

  public Effect<Done> addTask(AddTaskRequest request) {
    if (currentState().listId().isEmpty()) {
      return effects().error("Task list does not exist");
    }
    return effects()
        .persist(new TaskListEvent.TaskAdded(request.taskId(), request.description()))
        .thenReply(__ -> done());
  }

  public record ClaimTaskRequest(String taskId, String claimedBy) {}

  public Effect<Done> claimTask(ClaimTaskRequest request) {
    if (currentState().listId().isEmpty()) {
      return effects().error("Task list does not exist");
    }
    var task =
        currentState().tasks().stream()
            .filter(t -> t.taskId().equals(request.taskId()))
            .findFirst()
            .orElse(null);
    if (task == null) {
      return effects().error("Task not found: " + request.taskId());
    }
    if (task.status() != TaskListState.TaskListItemStatus.AVAILABLE) {
      return effects().error("Task is not available: " + request.taskId());
    }
    return effects()
        .persist(new TaskListEvent.TaskClaimed(request.taskId(), request.claimedBy()))
        .thenReply(__ -> done());
  }

  public Effect<Done> unclaimTask(String taskId) {
    if (currentState().listId().isEmpty()) {
      return effects().error("Task list does not exist");
    }
    return effects().persist(new TaskListEvent.TaskUnclaimed(taskId)).thenReply(__ -> done());
  }

  public Effect<Done> completeTask(String taskId) {
    if (currentState().listId().isEmpty()) {
      return effects().error("Task list does not exist");
    }
    return effects().persist(new TaskListEvent.TaskCompleted(taskId)).thenReply(__ -> done());
  }

  public Effect<Done> failTask(String taskId) {
    if (currentState().listId().isEmpty()) {
      return effects().error("Task list does not exist");
    }
    return effects().persist(new TaskListEvent.TaskFailed(taskId)).thenReply(__ -> done());
  }

  public Effect<Done> cancelTask(String taskId) {
    if (currentState().listId().isEmpty()) {
      return effects().error("Task list does not exist");
    }
    return effects().persist(new TaskListEvent.TaskCancelled(taskId)).thenReply(__ -> done());
  }

  public ReadOnlyEffect<TaskListState> getState() {
    if (currentState().listId().isEmpty()) {
      return effects().error("Task list does not exist");
    }
    return effects().reply(currentState());
  }

  @Override
  public TaskListState applyEvent(TaskListEvent event) {
    return switch (event) {
      case TaskListEvent.TaskListCreated e -> new TaskListState(e.listId(), currentState().tasks());
      case TaskListEvent.TaskAdded e -> currentState().withTask(e.taskId(), e.description());
      case TaskListEvent.TaskClaimed e -> currentState().withClaimed(e.taskId(), e.claimedBy());
      case TaskListEvent.TaskUnclaimed e -> currentState().withUnclaimed(e.taskId());
      case TaskListEvent.TaskCompleted e -> currentState().withCompleted(e.taskId());
      case TaskListEvent.TaskFailed e -> currentState().withFailed(e.taskId());
      case TaskListEvent.TaskCancelled e -> currentState().withCancelled(e.taskId());
    };
  }
}
