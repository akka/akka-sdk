/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.task;

import akka.javasdk.annotations.TypeName;
import java.util.List;

/**
 * Events persisted by {@link TaskEntity}, one per lifecycle transition. Subscribe to them with a
 * consuming Consumer to react to task progress, for example to trigger work when a task completes.
 */
public sealed interface TaskEvent {

  /** The task was created and is {@code PENDING}. */
  @TypeName("akka-task-created")
  record TaskCreated(
      String taskId,
      String name,
      String description,
      String instructions,
      String resultTypeName,
      List<String> dependencyTaskIds,
      List<TaskAttachment> attachments,
      List<String> ruleClassNames)
      implements TaskEvent {}

  /** The task was assigned to an owner. */
  @TypeName("akka-task-assigned")
  record TaskAssigned(String taskId, String name, String assignee) implements TaskEvent {}

  /** Work on the task started. */
  @TypeName("akka-task-started")
  record TaskStarted(String taskId, String name) implements TaskEvent {}

  /** A {@link TaskRule} rejected the completion result. */
  @TypeName("akka-task-result-rejected")
  record TaskResultRejected(String taskId, String name, String ruleClassName, String reason)
      implements TaskEvent {}

  /** The task completed with a serialized result. */
  @TypeName("akka-task-completed")
  record TaskCompleted(String taskId, String name, String result) implements TaskEvent {}

  /** The task failed during execution. */
  @TypeName("akka-task-failed")
  record TaskFailed(String taskId, String name, String reason) implements TaskEvent {}

  /** The task was cancelled before execution began. */
  @TypeName("akka-task-cancelled")
  record TaskCancelled(String taskId, String name, String reason) implements TaskEvent {}

  /** The task was reassigned to a new owner, with context for the handover. */
  @TypeName("akka-task-reassigned")
  record TaskReassigned(String taskId, String name, String newAssignee, String context)
      implements TaskEvent {}
}
