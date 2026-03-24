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
import java.util.List;

@Component(id = "akka-task")
public final class TaskEntity extends EventSourcedEntity<TaskState, TaskEvent> {

  private final String taskId;
  private final NotificationPublisher<TaskNotification> notificationPublisher;

  public TaskEntity(
      EventSourcedEntityContext context,
      NotificationPublisher<TaskNotification> notificationPublisher) {
    this.taskId = context.entityId();
    this.notificationPublisher = notificationPublisher;
  }

  public record CreateRequest(
      String name,
      String description,
      String instructions,
      String resultTypeName,
      List<String> dependencyTaskIds,
      List<TaskAttachment> attachments) {}

  public record ReassignRequest(String newAssignee, String context) {}

  @Override
  public TaskState emptyState() {
    return TaskState.empty();
  }

  public Effect<Done> create(CreateRequest request) {
    if (!currentState().taskId().isEmpty()) {
      return effects().error("Task already exists");
    }
    var deps =
        request.dependencyTaskIds() != null ? request.dependencyTaskIds() : List.<String>of();
    var atts = request.attachments() != null ? request.attachments() : List.<TaskAttachment>of();
    return effects()
        .persist(
            new TaskEvent.TaskCreated(
                taskId,
                request.name(),
                request.description(),
                request.instructions(),
                request.resultTypeName(),
                deps,
                atts))
        .thenReply(__ -> done());
  }

  public Effect<Done> assign(String assignee) {
    if (currentState().taskId().isEmpty()) {
      return effects().error("Task does not exist");
    }
    if (currentState().status() != TaskStatus.PENDING) {
      return effects().error("Task can only be assigned when PENDING");
    }
    if (currentState().assignee() != null) {
      return effects().error("Task is already assigned to " + currentState().assignee());
    }
    return effects().persist(new TaskEvent.TaskAssigned(taskId, assignee)).thenReply(__ -> done());
  }

  public Effect<Done> start() {
    if (currentState().taskId().isEmpty()) {
      return effects().error("Task does not exist");
    }
    if (currentState().status() != TaskStatus.ASSIGNED) {
      return effects().error("Task can only be started when ASSIGNED");
    }
    return effects().persist(new TaskEvent.TaskStarted(taskId)).thenReply(__ -> done());
  }

  public Effect<Done> complete(String result) {
    if (isTerminal()) {
      return effects().reply(done()); // idempotent for any terminal state
    }
    if (currentState().status() != TaskStatus.ASSIGNED
        && currentState().status() != TaskStatus.IN_PROGRESS) {
      return effects().error("Task can only be completed when ASSIGNED or IN_PROGRESS");
    }
    return effects()
        .persist(new TaskEvent.TaskCompleted(taskId, result))
        .thenReply(
            __ -> {
              notificationPublisher.publish(new TaskNotification.Completed(taskId, result));
              return done();
            });
  }

  public Effect<Done> fail(String reason) {
    if (isTerminal()) {
      return effects().reply(done()); // idempotent for any terminal state
    }
    if (currentState().status() != TaskStatus.ASSIGNED
        && currentState().status() != TaskStatus.IN_PROGRESS) {
      return effects().error("Task can only be failed when ASSIGNED or IN_PROGRESS");
    }
    return effects()
        .persist(new TaskEvent.TaskFailed(taskId, reason))
        .thenReply(
            __ -> {
              notificationPublisher.publish(new TaskNotification.Failed(taskId, reason));
              return done();
            });
  }

  public Effect<Done> cancel(String reason) {
    if (isTerminal()) {
      return effects().reply(done()); // idempotent for any terminal state
    }
    if (currentState().status() != TaskStatus.PENDING
        && currentState().status() != TaskStatus.ASSIGNED) {
      return effects().error("Task can only be cancelled when PENDING or ASSIGNED");
    }
    return effects()
        .persist(new TaskEvent.TaskCancelled(taskId, reason))
        .thenReply(
            __ -> {
              notificationPublisher.publish(new TaskNotification.Cancelled(taskId, reason));
              return done();
            });
  }

  public Effect<Done> reassign(ReassignRequest request) {
    if (currentState().taskId().isEmpty()) {
      return effects().error("Task does not exist");
    }
    if (currentState().status() != TaskStatus.IN_PROGRESS) {
      return effects().error("Task can only be reassigned when IN_PROGRESS");
    }
    return effects()
        .persist(new TaskEvent.TaskReassigned(taskId, request.newAssignee(), request.context()))
        .thenReply(__ -> done());
  }

  private boolean isTerminal() {
    return currentState().status() == TaskStatus.COMPLETED
        || currentState().status() == TaskStatus.FAILED
        || currentState().status() == TaskStatus.CANCELLED;
  }

  public NotificationPublisher.NotificationStream<TaskNotification> notifications() {
    return notificationPublisher.stream();
  }

  public ReadOnlyEffect<TaskState> getState() {
    if (currentState().taskId().isEmpty()) {
      return effects().error("Task does not exist");
    }
    return effects().reply(currentState());
  }

  @Override
  public TaskState applyEvent(TaskEvent event) {
    return switch (event) {
      case TaskEvent.TaskCreated e ->
          new TaskState(
              e.taskId(),
              e.name(),
              e.description(),
              e.instructions(),
              TaskStatus.PENDING,
              e.resultTypeName(),
              null,
              null,
              e.dependencyTaskIds() != null ? e.dependencyTaskIds() : List.of(),
              null,
              e.attachments() != null ? e.attachments() : List.of(),
              List.of());
      case TaskEvent.TaskAssigned e -> currentState().withAssignee(e.assignee());
      case TaskEvent.TaskStarted e -> currentState().withStatus(TaskStatus.IN_PROGRESS);
      case TaskEvent.TaskCompleted e -> currentState().withResult(e.result());
      case TaskEvent.TaskFailed e -> currentState().withFailure(e.reason());
      case TaskEvent.TaskCancelled e -> currentState().withCancellation(e.reason());
      case TaskEvent.TaskReassigned e ->
          currentState().withReassignment(e.newAssignee(), e.context());
    };
  }
}
