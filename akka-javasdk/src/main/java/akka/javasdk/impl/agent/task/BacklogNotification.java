/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent.task;

import akka.annotation.InternalApi;
import akka.javasdk.annotations.TypeName;

/** INTERNAL API Notifications published by a BacklogEntity when its state changes. */
@InternalApi
public sealed interface BacklogNotification {

  @TypeName("akka-backlog-notification-created")
  record BacklogCreated(String name) implements BacklogNotification {}

  @TypeName("akka-backlog-notification-task-added")
  record TaskAdded(String taskId) implements BacklogNotification {}

  @TypeName("akka-backlog-notification-task-claimed")
  record TaskClaimed(String taskId, String claimedBy) implements BacklogNotification {}

  @TypeName("akka-backlog-notification-task-released")
  record TaskReleased(String taskId) implements BacklogNotification {}

  @TypeName("akka-backlog-notification-task-transferred")
  record TaskTransferred(String taskId, String transferredTo) implements BacklogNotification {}

  @TypeName("akka-backlog-notification-unclaimed-cancelled")
  record UnclaimedCancelled() implements BacklogNotification {}

  @TypeName("akka-backlog-notification-closed")
  record BacklogClosed() implements BacklogNotification {}
}
