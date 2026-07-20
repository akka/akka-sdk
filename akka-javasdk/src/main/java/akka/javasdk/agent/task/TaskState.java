/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.task;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** State of a task entity. */
public record TaskState(
    String taskId,
    String name,
    String description,
    String instructions,
    TaskStatus status,
    String resultTypeName,
    Optional<String> result,
    Optional<String> failureReason,
    List<String> dependencyTaskIds,
    Optional<String> assignee,
    List<TaskAttachment> attachments,
    List<String> reassignmentContext,
    List<String> ruleClassNames) {

  /** The state of a task that has not been created yet. */
  public static TaskState empty() {
    return new TaskState(
        "",
        "",
        "",
        null,
        TaskStatus.PENDING,
        null,
        Optional.empty(),
        Optional.empty(),
        List.of(),
        Optional.empty(),
        List.of(),
        List.of(),
        List.of());
  }

  /** Copy with the given status. */
  public TaskState withStatus(TaskStatus status) {
    return new TaskState(
        taskId,
        name,
        description,
        instructions,
        status,
        resultTypeName,
        result,
        failureReason,
        dependencyTaskIds,
        assignee,
        attachments,
        reassignmentContext,
        ruleClassNames);
  }

  /** Copy with the given assignee, moving to {@code ASSIGNED}. */
  public TaskState withAssignee(String assignee) {
    return new TaskState(
        taskId,
        name,
        description,
        instructions,
        TaskStatus.ASSIGNED,
        resultTypeName,
        result,
        failureReason,
        dependencyTaskIds,
        Optional.of(assignee),
        attachments,
        reassignmentContext,
        ruleClassNames);
  }

  /** Copy with the given result, moving to {@code COMPLETED}. */
  public TaskState withResult(String result) {
    return new TaskState(
        taskId,
        name,
        description,
        instructions,
        TaskStatus.COMPLETED,
        resultTypeName,
        Optional.of(result),
        failureReason,
        dependencyTaskIds,
        assignee,
        attachments,
        reassignmentContext,
        ruleClassNames);
  }

  /** Copy with the rejection reason, moving to {@code RESULT_REJECTED}. */
  public TaskState withResultRejection(String reason) {
    return new TaskState(
        taskId,
        name,
        description,
        instructions,
        TaskStatus.RESULT_REJECTED,
        resultTypeName,
        result,
        Optional.of(reason),
        dependencyTaskIds,
        assignee,
        attachments,
        reassignmentContext,
        ruleClassNames);
  }

  /** Copy with the failure reason, moving to {@code FAILED}. */
  public TaskState withFailure(String reason) {
    return new TaskState(
        taskId,
        name,
        description,
        instructions,
        TaskStatus.FAILED,
        resultTypeName,
        result,
        Optional.of(reason),
        dependencyTaskIds,
        assignee,
        attachments,
        reassignmentContext,
        ruleClassNames);
  }

  /** Copy with the cancellation reason, moving to {@code CANCELLED}. */
  public TaskState withCancellation(String reason) {
    return new TaskState(
        taskId,
        name,
        description,
        instructions,
        TaskStatus.CANCELLED,
        resultTypeName,
        result,
        Optional.of(reason),
        dependencyTaskIds,
        assignee,
        attachments,
        reassignmentContext,
        ruleClassNames);
  }

  /** Copy with a new assignee, appending the handover context. */
  public TaskState withReassignment(String newAssignee, String context) {
    var updated = new ArrayList<>(this.reassignmentContext);
    updated.add(context);
    return new TaskState(
        taskId,
        name,
        description,
        instructions,
        status,
        resultTypeName,
        result,
        failureReason,
        dependencyTaskIds,
        Optional.of(newAssignee),
        attachments,
        List.copyOf(updated),
        ruleClassNames);
  }
}
