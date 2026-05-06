/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.autonomous;

import akka.annotation.DoNotInherit;
import java.util.List;
import java.util.Optional;

/**
 * Notifications published by the runtime for an autonomous agent instance.
 *
 * <p>These events are emitted automatically by the runtime as the agent progresses through its
 * execution loop. They are not published by user code.
 *
 * <p>Subscribe to notifications via {@link
 * akka.javasdk.client.AutonomousAgentClient#notificationStream()}.
 *
 * <p>Notifications are grouped by capability into marker sub-interfaces (for example {@link
 * LifecycleNotification}, {@link TaskNotification}, {@link TeamNotification}). User code can match
 * on a marker to handle a whole family generically.
 */
@DoNotInherit
public sealed interface Notification {

  // -- Marker interfaces -----------------------------------------------------

  /** Lifecycle notifications: agent activation, iteration boundaries, pause/resume/stop. */
  sealed interface LifecycleNotification extends Notification {}

  /** Task notifications: assignment, start, completion, failure, cancellation, dependencies. */
  sealed interface TaskNotification extends Notification {}

  /** Handoff notifications: source-side and target-side of cross-agent handoffs. */
  sealed interface HandoffNotification extends Notification {}

  /** Delegation notifications: orchestrator-side and worker-side of subtask delegation. */
  sealed interface DelegationNotification extends Notification {}

  /** Team notifications: team formation, member lifecycle, disbanding. */
  sealed interface TeamNotification extends Notification {}

  /** Backlog notifications: backlog assignment/access and task claims. */
  sealed interface BacklogNotification extends Notification {}

  /** Conversation notifications: conversation creation, turns, participation. */
  sealed interface ConversationNotification extends Notification {}

  /** Messaging notifications: contact introductions and message delivery. */
  sealed interface MessagingNotification extends Notification {}

  /**
   * Struggle signals: derived notifications for repeated failures, stuck deps, approaching limits.
   */
  sealed interface StruggleNotification extends Notification {}

  // -- Lifecycle -------------------------------------------------------------

  /** Agent activated — transitioned from idle to processing. */
  record Activated() implements LifecycleNotification {}

  /** Agent deactivated — no more work, back to idle. */
  record Deactivated() implements LifecycleNotification {}

  /** Agent iteration started — LLM call beginning. */
  record IterationStarted() implements LifecycleNotification {}

  /** Agent iteration completed successfully. */
  record IterationCompleted(AutonomousAgent.TokenUsage tokenUsage)
      implements LifecycleNotification {}

  /**
   * Agent iteration failed. {@code taskId} and {@code iterationNumber} are populated when the
   * failure happened during a task iteration (i.e. in the model loop driving a specific task).
   */
  record IterationFailed(String reason, Optional<String> taskId, Optional<Integer> iterationNumber)
      implements LifecycleNotification {}

  /** Agent suspended. The reason identifies the source (e.g. "operator"). */
  record Suspended(String reason) implements LifecycleNotification {}

  /** Agent resumed. The reason identifies the source (e.g. "operator"). */
  record Resumed(String reason) implements LifecycleNotification {}

  /**
   * Agent stopped. The reason distinguishes "operator" (explicit stop) from "auto-stopped" (queue
   * drained).
   */
  record Stopped(String reason) implements LifecycleNotification {}

  // -- Task ------------------------------------------------------------------

  /** A task was assigned (queued) — fires before the model loop begins setup for it. */
  record TaskAssigned(String taskId) implements TaskNotification {}

  /** Agent started working on a task. */
  record TaskStarted(String taskId, String taskName) implements TaskNotification {}

  /** Agent's task result was rejected by a validation rule. */
  record TaskResultRejected(String taskId, String taskName, String reason)
      implements TaskNotification {}

  /** Agent completed a task successfully. */
  record TaskCompleted(String taskId, String taskName) implements TaskNotification {}

  /** Agent failed a task via explicit fail_task. */
  record TaskFailed(String taskId, String taskName, String reason) implements TaskNotification {}

  /**
   * Task was cancelled by the framework — dependency failure, max iterations, orphan cleanup, etc.
   * Distinct from {@link TaskFailed}, which represents the agent's explicit decision to fail the
   * task.
   */
  record TaskCancelled(String taskId, String taskName, String reason) implements TaskNotification {}

  /** A task is blocked waiting for one or more dependency tasks to resolve. */
  record TaskDependencyWait(String taskId, List<String> pendingDependencyTaskIds)
      implements TaskNotification {}

  /** A specific dependency for a task has resolved (success or failure). */
  record DependencyResolved(String taskId, String dependencyTaskId, boolean success, String reason)
      implements TaskNotification {}

  // -- Handoff ---------------------------------------------------------------

  /** Source side: this agent has handed off a task to another agent. */
  record HandoffStarted(
      String taskId, String taskName, String targetComponentId, String targetInstanceId)
      implements HandoffNotification {}

  /**
   * Target side: this agent has received a handed-off task from another agent. Only the task ID is
   * known at acceptance time — the task definition (and name) is loaded later during setup.
   */
  record HandoffReceived(String taskId, String sourceComponentId, String sourceInstanceId)
      implements HandoffNotification {}

  // -- Delegation ------------------------------------------------------------

  /**
   * Orchestrator side: a batch of subtasks has been dispatched to workers. For request-based
   * delegations the per-subtask id lists are empty; {@link #delegationCount()} remains
   * authoritative.
   */
  record DelegationStarted(
      List<String> workerComponentIds,
      int delegationCount,
      List<String> subtaskIds,
      List<String> workerInstanceIds)
      implements DelegationNotification {}

  /** Orchestrator side: aggregate resolution of a delegation batch. */
  record DelegationResolved(
      int succeeded, int failed, List<String> succeededSubtaskIds, List<String> failedSubtaskIds)
      implements DelegationNotification {}

  /** Worker side: this agent accepted a delegated subtask from the orchestrator. */
  record WorkerTaskReceived(
      String subtaskId, String orchestratorComponentId, String orchestratorInstanceId)
      implements DelegationNotification {}

  /** Worker side: this agent finished its delegated subtask. */
  record WorkerTaskCompleted(String subtaskId) implements DelegationNotification {}

  // -- Team ------------------------------------------------------------------

  /** Team lead: a new team was formed with the given members. */
  record TeamCreated(String teamId, List<String> memberComponentIds, List<String> memberInstanceIds)
      implements TeamNotification {}

  /** Team lead: a team member's setup chain completed and the member is now active. */
  record TeamMemberReady(String teamId, String memberComponentId, String memberInstanceId)
      implements TeamNotification {}

  /** Team lead: a team member's setup chain failed. */
  record TeamMemberSetupFailed(
      String teamId, String memberComponentId, String memberInstanceId, String reason)
      implements TeamNotification {}

  /** Team lead: a team member has stopped. */
  record TeamMemberStopped(String teamId, String memberComponentId, String memberInstanceId)
      implements TeamNotification {}

  /** Team lead: a team has been disbanded. */
  record TeamDisbanded(String teamId) implements TeamNotification {}

  /** Team member: this agent joined a team led by the given agent. */
  record TeamJoined(String leadComponentId, String leadInstanceId) implements TeamNotification {}

  // -- Backlog ---------------------------------------------------------------

  /** Backlog management: a backlog has been assigned to this agent. */
  record BacklogAssigned(String backlogId, String backlogName) implements BacklogNotification {}

  /** Backlog management: a backlog has been closed. */
  record BacklogClosed(String backlogId, String backlogName) implements BacklogNotification {}

  /** Backlog access: this agent has been granted access to a backlog. */
  record BacklogAccessGranted(String backlogId, String backlogName)
      implements BacklogNotification {}

  /** Backlog access: this agent claimed a task from a backlog. */
  record BacklogTaskClaimed(String backlogId, String backlogName, String taskId)
      implements BacklogNotification {}

  // -- Conversation ----------------------------------------------------------

  /** Conversation moderator: a new conversation was created with the given participants. */
  record ConversationCreated(
      String conversationId,
      String pattern,
      String topic,
      List<String> participantComponentIds,
      List<String> participantInstanceIds)
      implements ConversationNotification {}

  /** Conversation moderator: a participant's setup completed and they are ready to take turns. */
  record ConversationParticipantReady(
      String conversationId, String participantComponentId, String participantInstanceId)
      implements ConversationNotification {}

  /** Conversation moderator: a participant's setup failed. */
  record ConversationParticipantSetupFailed(
      String conversationId,
      String participantComponentId,
      String participantInstanceId,
      String reason)
      implements ConversationNotification {}

  /** Conversation moderator: a conversation has ended. */
  record ConversationEnded(String conversationId) implements ConversationNotification {}

  /** Conversation moderator: a turn was received from a participant. */
  record ConversationTurnReceived(
      String conversationId, String participantComponentId, String participantInstanceId)
      implements ConversationNotification {}

  /** Conversation participant: this agent joined a conversation moderated by the given agent. */
  record ConversationJoined(
      String conversationId, String moderatorComponentId, String moderatorInstanceId)
      implements ConversationNotification {}

  /**
   * Conversation participant: this agent submitted its turn after the given number of iterations.
   */
  record ParticipantTurnSubmitted(String conversationId, int iterationsUsed)
      implements ConversationNotification {}

  // -- Messaging -------------------------------------------------------------

  /** A message was received from another agent. */
  record MessageReceived(String fromComponentId, String fromInstanceId, String text)
      implements MessagingNotification {}

  /** A new contact was introduced to this agent. */
  record ContactAdded(String contactComponentId, String contactInstanceId)
      implements MessagingNotification {}

  // -- Struggle signals ------------------------------------------------------

  /**
   * Derived signal: a task is struggling — N consecutive iteration failures, M consecutive result
   * rejections, or some other impediment captured in {@link #reason()}. Fires once per detection;
   * the underlying counters reset on task termination or a successful recovery.
   */
  record TaskStruggleDetected(
      String taskId, String taskName, String reason, int iteration, int maxIterations)
      implements StruggleNotification {}

  /**
   * Derived signal: a task waiting on dependencies has been waiting longer than the configured
   * threshold. Re-fires periodically until the dependency resolves.
   */
  record TaskDependencyStuck(
      String taskId, List<String> pendingDependencyTaskIds, long waitDurationSeconds)
      implements StruggleNotification {}

  /**
   * Derived signal: a task is at or beyond a configurable fraction (default 80%) of its max
   * iterations. Useful as a heads-up before the framework cancels with "max iterations".
   */
  record TaskApproachingMaxIterations(
      String taskId, String taskName, int iteration, int maxIterations)
      implements StruggleNotification {}

  /**
   * Derived signal: N consecutive iteration failures in flows not tied to a specific task (pre-task
   * setup, request-based delegation). Same threshold as {@link TaskStruggleDetected} for iteration
   * failures, surfaced separately so subscribers don't have to disambiguate.
   */
  record RepeatedIterationFailure(int iterationsFailed, String lastReason)
      implements StruggleNotification {}
}
