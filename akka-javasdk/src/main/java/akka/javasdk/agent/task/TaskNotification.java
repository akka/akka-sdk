/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.task;

import akka.javasdk.annotations.TypeName;

/** Notifications published by task entities when they reach a terminal state. */
public sealed interface TaskNotification {

  String taskId();

  @TypeName("akka-task-notification-completed")
  record Completed(String taskId, String result) implements TaskNotification {}

  @TypeName("akka-task-notification-failed")
  record Failed(String taskId, String reason) implements TaskNotification {}

  @TypeName("akka-task-notification-cancelled")
  record Cancelled(String taskId, String reason) implements TaskNotification {}
}
