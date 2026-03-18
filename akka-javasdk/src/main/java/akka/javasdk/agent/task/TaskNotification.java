/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.task;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Notification events published for real-time task lifecycle observation. Subscribers receive these
 * notifications as a stream, keyed by task ID — notifications follow the task regardless of which
 * agent is processing it.
 *
 * <p>Each notification type maps 1:1 to a {@link TaskEvent} variant. The {@link TaskEventConsumer}
 * translates persisted entity events into notifications automatically.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "@type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = TaskNotification.TaskCreated.class, name = "task-created"),
  @JsonSubTypes.Type(value = TaskNotification.TaskAssigned.class, name = "task-assigned"),
  @JsonSubTypes.Type(value = TaskNotification.TaskStarted.class, name = "task-started"),
  @JsonSubTypes.Type(value = TaskNotification.TaskCompleted.class, name = "task-completed"),
  @JsonSubTypes.Type(value = TaskNotification.TaskFailed.class, name = "task-failed"),
  @JsonSubTypes.Type(value = TaskNotification.TaskHandedOff.class, name = "task-handed-off"),
  @JsonSubTypes.Type(value = TaskNotification.ApprovalRequired.class, name = "approval-required"),
  @JsonSubTypes.Type(value = TaskNotification.Approved.class, name = "approved"),
  @JsonSubTypes.Type(value = TaskNotification.Rejected.class, name = "rejected")
})
public sealed interface TaskNotification {

  record TaskCreated(String taskId, String description) implements TaskNotification {}

  record TaskAssigned(String taskId, String assignee) implements TaskNotification {}

  record TaskStarted(String taskId) implements TaskNotification {}

  record TaskCompleted(String taskId, String result) implements TaskNotification {}

  record TaskFailed(String taskId, String reason) implements TaskNotification {}

  record TaskHandedOff(String taskId, String newAssignee) implements TaskNotification {}

  // Policy approval notifications

  record ApprovalRequired(String taskId, String reason) implements TaskNotification {}

  record Approved(String taskId) implements TaskNotification {}

  record Rejected(String taskId, String reason) implements TaskNotification {}
}
