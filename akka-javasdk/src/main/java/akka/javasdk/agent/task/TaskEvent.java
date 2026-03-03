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
      String name,
      String description,
      String instructions,
      String resultTypeName,
      List<String> dependencyTaskIds,
      List<TaskAttachment> attachments)
      implements TaskEvent {}

  @TypeName("akka-task-assigned")
  record TaskAssigned(String taskId, String assignee) implements TaskEvent {}

  @TypeName("akka-task-started")
  record TaskStarted(String taskId) implements TaskEvent {}

  @TypeName("akka-task-completed")
  record TaskCompleted(String taskId, String result) implements TaskEvent {}

  @TypeName("akka-task-failed")
  record TaskFailed(String taskId, String reason) implements TaskEvent {}
}
