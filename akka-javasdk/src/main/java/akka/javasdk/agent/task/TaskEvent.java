/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.task;

import akka.javasdk.annotations.TypeName;
import java.util.List;

public sealed interface TaskEvent {

  @TypeName("akka-task-created")
  record TaskCreated(
      String taskId,
      String description,
      String instructions,
      String resultTypeName,
      List<String> dependencyTaskIds,
      List<ContentRef> contentRefs,
      List<String> policyClassNames)
      implements TaskEvent {}

  @TypeName("akka-task-assigned")
  record TaskAssigned(String taskId, String assignee) implements TaskEvent {}

  @TypeName("akka-task-started")
  record TaskStarted(String taskId) implements TaskEvent {}

  @TypeName("akka-task-completed")
  record TaskCompleted(String taskId, String result) implements TaskEvent {}

  @TypeName("akka-task-failed")
  record TaskFailed(String taskId, String reason) implements TaskEvent {}

  @TypeName("akka-task-handed-off")
  record TaskHandedOff(String taskId, String newAssignee, String context) implements TaskEvent {}

  @TypeName("akka-task-approval-requested")
  record ApprovalRequested(String taskId, String pendingResult, String reason)
      implements TaskEvent {}

  @TypeName("akka-task-approved")
  record TaskApproved(String taskId) implements TaskEvent {}

  @TypeName("akka-task-rejected")
  record TaskRejected(String taskId, String reason) implements TaskEvent {}
}
