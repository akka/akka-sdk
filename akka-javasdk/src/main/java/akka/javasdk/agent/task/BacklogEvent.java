/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.task;

import akka.javasdk.annotations.TypeName;

public sealed interface BacklogEvent {

  @TypeName("akka-backlog-created")
  record BacklogCreated(String name) implements BacklogEvent {}

  @TypeName("akka-backlog-task-added")
  record TaskAdded(String taskId) implements BacklogEvent {}

  @TypeName("akka-backlog-task-claimed")
  record TaskClaimed(String taskId, String claimedBy) implements BacklogEvent {}

  @TypeName("akka-backlog-task-released")
  record TaskReleased(String taskId) implements BacklogEvent {}

  @TypeName("akka-backlog-task-transferred")
  record TaskTransferred(String taskId, String transferredTo) implements BacklogEvent {}

  @TypeName("akka-backlog-unclaimed-cancelled")
  record UnclaimedCancelled() implements BacklogEvent {}
}
