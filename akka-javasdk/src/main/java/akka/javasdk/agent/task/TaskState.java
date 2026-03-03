/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.task;

import java.util.List;

/** State of a task entity. */
public record TaskState(
    String taskId,
    String name,
    String description,
    String instructions,
    TaskStatus status,
    String resultTypeName,
    String result,
    String failureReason,
    List<String> dependencyTaskIds,
    String assignee,
    List<TaskAttachment> attachments) {

  public static TaskState empty() {
    return new TaskState(
        "", "", "", null, TaskStatus.PENDING, null, null, null, List.of(), null, List.of());
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
        attachments);
  }

  public TaskState withAssignee(String assignee) {
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
        attachments);
  }

  public TaskState withResult(String result) {
    return new TaskState(
        taskId,
        name,
        description,
        instructions,
        TaskStatus.COMPLETED,
        resultTypeName,
        result,
        failureReason,
        dependencyTaskIds,
        assignee,
        attachments);
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
        reason,
        dependencyTaskIds,
        assignee,
        attachments);
  }
}
