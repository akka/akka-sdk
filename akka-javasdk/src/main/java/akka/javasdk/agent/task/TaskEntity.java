/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.task;

import static akka.Done.done;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import java.util.List;

@Component(id = "akka-task")
public final class TaskEntity extends EventSourcedEntity<TaskState, TaskEvent> {

  private final String taskId;

  public TaskEntity(EventSourcedEntityContext context) {
    this.taskId = context.entityId();
  }

  public record CreateRequest(
      String description,
      String instructions,
      String resultTypeName,
      List<String> dependencyTaskIds,
      List<ContentRef> contentRefs,
      List<String> policyClassNames) {

    /** Convenience constructor without instructions, dependencies, content, or policies. */
    public CreateRequest(String description, String resultTypeName) {
      this(description, null, resultTypeName, List.of(), List.of(), List.of());
    }

    /** Convenience constructor without instructions, content, or policies. */
    public CreateRequest(
        String description, String resultTypeName, List<String> dependencyTaskIds) {
      this(description, null, resultTypeName, dependencyTaskIds, List.of(), List.of());
    }

    /** Convenience constructor without content or policies. */
    public CreateRequest(
        String description,
        String instructions,
        String resultTypeName,
        List<String> dependencyTaskIds) {
      this(description, instructions, resultTypeName, dependencyTaskIds, List.of(), List.of());
    }

    /** Convenience constructor without policies. */
    public CreateRequest(
        String description,
        String instructions,
        String resultTypeName,
        List<String> dependencyTaskIds,
        List<ContentRef> contentRefs) {
      this(description, instructions, resultTypeName, dependencyTaskIds, contentRefs, List.of());
    }
  }

  public record HandoffRequest(String newAssignee, String context) {}

  public record AwaitApprovalRequest(String pendingResult, String reason) {}

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
    var refs = request.contentRefs() != null ? request.contentRefs() : List.<ContentRef>of();
    var policies =
        request.policyClassNames() != null ? request.policyClassNames() : List.<String>of();
    return effects()
        .persist(
            new TaskEvent.TaskCreated(
                taskId,
                request.description(),
                request.instructions(),
                request.resultTypeName(),
                deps,
                refs,
                policies))
        .thenReply(__ -> done());
  }

  public Effect<Done> assign(String assignee) {
    if (currentState().taskId().isEmpty()) {
      return effects().error("Task does not exist");
    }
    if (currentState().status() != TaskStatus.PENDING) {
      return effects().error("Task can only be assigned when PENDING");
    }
    return effects().persist(new TaskEvent.TaskAssigned(taskId, assignee)).thenReply(__ -> done());
  }

  public Effect<Done> start() {
    if (currentState().taskId().isEmpty()) {
      return effects().error("Task does not exist");
    }
    if (currentState().status() != TaskStatus.PENDING) {
      return effects().error("Task can only be started when PENDING");
    }
    if (currentState().assignee().isEmpty()) {
      return effects().error("Task must be assigned before starting");
    }
    return effects().persist(new TaskEvent.TaskStarted(taskId)).thenReply(__ -> done());
  }

  public Effect<Done> complete(String result) {
    if (currentState().status() == TaskStatus.COMPLETED
        || currentState().status() == TaskStatus.FAILED) {
      return effects().reply(done()); // idempotent for any terminal state
    }
    if (currentState().status() != TaskStatus.IN_PROGRESS) {
      return effects().error("Task can only be completed when IN_PROGRESS");
    }
    return effects().persist(new TaskEvent.TaskCompleted(taskId, result)).thenReply(__ -> done());
  }

  public Effect<Done> fail(String reason) {
    if (currentState().status() == TaskStatus.COMPLETED
        || currentState().status() == TaskStatus.FAILED) {
      return effects().reply(done()); // idempotent for any terminal state
    }
    if (currentState().status() != TaskStatus.IN_PROGRESS
        && currentState().status() != TaskStatus.AWAITING_APPROVAL) {
      return effects().error("Task can only be failed when IN_PROGRESS or AWAITING_APPROVAL");
    }
    return effects().persist(new TaskEvent.TaskFailed(taskId, reason)).thenReply(__ -> done());
  }

  public Effect<Done> handoff(HandoffRequest request) {
    if (currentState().taskId().isEmpty()) {
      return effects().error("Task does not exist");
    }
    if (currentState().status() != TaskStatus.IN_PROGRESS) {
      return effects().error("Task can only be handed off when IN_PROGRESS");
    }
    return effects()
        .persist(new TaskEvent.TaskHandedOff(taskId, request.newAssignee(), request.context()))
        .thenReply(__ -> done());
  }

  public Effect<Done> awaitApproval(AwaitApprovalRequest request) {
    if (currentState().status() != TaskStatus.IN_PROGRESS) {
      return effects().error("Approval can only be requested when IN_PROGRESS");
    }
    return effects()
        .persist(new TaskEvent.ApprovalRequested(taskId, request.pendingResult(), request.reason()))
        .thenReply(__ -> done());
  }

  public Effect<Done> approve() {
    if (currentState().status() != TaskStatus.AWAITING_APPROVAL) {
      return effects().error("Task can only be approved when AWAITING_APPROVAL");
    }
    return effects().persist(new TaskEvent.TaskApproved(taskId)).thenReply(__ -> done());
  }

  public Effect<Done> reject(String reason) {
    if (currentState().status() != TaskStatus.AWAITING_APPROVAL) {
      return effects().error("Task can only be rejected when AWAITING_APPROVAL");
    }
    return effects().persist(new TaskEvent.TaskRejected(taskId, reason)).thenReply(__ -> done());
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
              e.description(),
              e.instructions(),
              TaskStatus.PENDING,
              "",
              e.resultTypeName(),
              null,
              List.of(),
              e.dependencyTaskIds() != null ? e.dependencyTaskIds() : List.of(),
              e.contentRefs() != null ? e.contentRefs() : List.of(),
              e.policyClassNames() != null ? e.policyClassNames() : List.of(),
              null,
              null);
      case TaskEvent.TaskAssigned e -> currentState().withAssignee(e.assignee());
      case TaskEvent.TaskStarted e -> currentState().withStatus(TaskStatus.IN_PROGRESS);
      case TaskEvent.TaskCompleted e ->
          currentState().withStatus(TaskStatus.COMPLETED).withResult(e.result());
      case TaskEvent.TaskFailed e ->
          currentState().withStatus(TaskStatus.FAILED).withResult(e.reason());
      case TaskEvent.TaskHandedOff e -> currentState().withHandoff(e.newAssignee(), e.context());
      case TaskEvent.ApprovalRequested e ->
          currentState().withApprovalRequested(e.pendingResult(), e.reason());
      case TaskEvent.TaskApproved e -> currentState().withApproved();
      case TaskEvent.TaskRejected e -> currentState().withRejected(e.reason());
    };
  }
}
