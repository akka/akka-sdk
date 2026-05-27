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
