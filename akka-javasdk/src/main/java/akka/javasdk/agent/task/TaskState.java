/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.task;

import java.util.ArrayList;
import java.util.List;

/** State of a task. */
public record TaskState(
    String taskId,
    String description,
    String instructions,
    TaskStatus status,
    String assignee,
    String resultTypeName,
    String result,
    List<String> handoffContext,
    List<String> dependencyTaskIds,
    List<ContentRef> contentRefs,
    List<String> policyClassNames,
    String pendingApprovalResult,
    String approvalReason) {

  public static TaskState empty() {
    return new TaskState(
        "",
        "",
        null,
        TaskStatus.PENDING,
        "",
        null,
        null,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        null,
        null);
  }

  public TaskState withStatus(TaskStatus status) {
    return new TaskState(
        taskId,
        description,
        instructions,
        status,
        assignee,
        resultTypeName,
        result,
        handoffContext,
        dependencyTaskIds,
        contentRefs,
        policyClassNames,
        pendingApprovalResult,
        approvalReason);
  }

  public TaskState withAssignee(String assignee) {
    return new TaskState(
        taskId,
        description,
        instructions,
        status,
        assignee,
        resultTypeName,
        result,
        handoffContext,
        dependencyTaskIds,
        contentRefs,
        policyClassNames,
        pendingApprovalResult,
        approvalReason);
  }

  public TaskState withResult(String result) {
    return new TaskState(
        taskId,
        description,
        instructions,
        status,
        assignee,
        resultTypeName,
        result,
        handoffContext,
        dependencyTaskIds,
        contentRefs,
        policyClassNames,
        pendingApprovalResult,
        approvalReason);
  }

  public TaskState withHandoff(String newAssignee, String context) {
    var updated = new ArrayList<>(handoffContext);
    updated.add(context);
    return new TaskState(
        taskId,
        description,
        instructions,
        status,
        newAssignee,
        resultTypeName,
        result,
        updated,
        dependencyTaskIds,
        contentRefs,
        policyClassNames,
        pendingApprovalResult,
        approvalReason);
  }

  public TaskState withApprovalRequested(String pendingResult, String reason) {
    return new TaskState(
        taskId,
        description,
        instructions,
        TaskStatus.AWAITING_APPROVAL,
        assignee,
        resultTypeName,
        result,
        handoffContext,
        dependencyTaskIds,
        contentRefs,
        policyClassNames,
        pendingResult,
        reason);
  }

  public TaskState withApproved() {
    return new TaskState(
        taskId,
        description,
        instructions,
        TaskStatus.COMPLETED,
        assignee,
        resultTypeName,
        pendingApprovalResult,
        handoffContext,
        dependencyTaskIds,
        contentRefs,
        policyClassNames,
        null,
        null);
  }

  public TaskState withRejected(String reason) {
    return new TaskState(
        taskId,
        description,
        instructions,
        TaskStatus.FAILED,
        assignee,
        resultTypeName,
        reason,
        handoffContext,
        dependencyTaskIds,
        contentRefs,
        policyClassNames,
        null,
        null);
  }
}
