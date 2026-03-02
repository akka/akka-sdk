/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.autonomous;

import akka.javasdk.annotations.TypeName;
import java.util.List;

public sealed interface TaskListEvent {

  @TypeName("akka-task-list-created")
  record TaskListCreated(String listId) implements TaskListEvent {}

  @TypeName("akka-task-list-task-added")
  record TaskAdded(
      String taskId, String description, List<String> targetAgentTypes, String resultTypeName)
      implements TaskListEvent {}

  @TypeName("akka-task-list-task-claimed")
  record TaskClaimed(String taskId, String claimedBy) implements TaskListEvent {}

  @TypeName("akka-task-list-task-unclaimed")
  record TaskUnclaimed(String taskId) implements TaskListEvent {}

  @TypeName("akka-task-list-task-completed")
  record TaskCompleted(String taskId) implements TaskListEvent {}

  @TypeName("akka-task-list-task-failed")
  record TaskFailed(String taskId) implements TaskListEvent {}

  @TypeName("akka-task-list-task-cancelled")
  record TaskCancelled(String taskId) implements TaskListEvent {}
}
