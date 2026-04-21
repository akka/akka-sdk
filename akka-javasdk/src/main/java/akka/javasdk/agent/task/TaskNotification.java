/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.task;

import akka.javasdk.annotations.TypeName;

/** Notifications published by task entities when they reach a terminal state. */
public sealed interface TaskNotification {

  String taskId();

  String taskName();

  @TypeName("akka-task-notification-completed")
  record Completed(String taskId, String taskName, String result) implements TaskNotification {}

  @TypeName("akka-task-notification-result-rejected")
  record ResultRejected(String taskId, String taskName, String ruleClassName, String reason)
      implements TaskNotification {}

  @TypeName("akka-task-notification-failed")
  record Failed(String taskId, String taskName, String reason) implements TaskNotification {}

  @TypeName("akka-task-notification-cancelled")
  record Cancelled(String taskId, String taskName, String reason) implements TaskNotification {}
}
