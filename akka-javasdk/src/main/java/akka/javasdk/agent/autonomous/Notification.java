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
  final class Activated implements LifecycleNotification {
    public Activated() {}
  }

  /** Agent deactivated — no more work, back to idle. */
  final class Deactivated implements LifecycleNotification {
    public Deactivated() {}
  }

  /** Agent iteration started — LLM call beginning. */
  final class IterationStarted implements LifecycleNotification {
    public IterationStarted() {}
  }

  /** Agent iteration completed successfully. */
  final class IterationCompleted implements LifecycleNotification {
    private final AutonomousAgent.TokenUsage tokenUsage;

    public IterationCompleted(AutonomousAgent.TokenUsage tokenUsage) {
      this.tokenUsage = tokenUsage;
    }

    /** Token usage for this iteration. */
    public AutonomousAgent.TokenUsage tokenUsage() {
      return tokenUsage;
    }
  }

  /**
   * Agent iteration failed. {@code taskId} and {@code iterationNumber} are populated when the
   * failure happened during a task iteration (i.e. in the model loop driving a specific task).
   */
  final class IterationFailed implements LifecycleNotification {
    private final String reason;
    private final Optional<String> taskId;
    private final Optional<Integer> iterationNumber;

    public IterationFailed(
        String reason, Optional<String> taskId, Optional<Integer> iterationNumber) {
      this.reason = reason;
      this.taskId = taskId;
      this.iterationNumber = iterationNumber;
    }

    /** The failure reason. */
    public String reason() {
      return reason;
    }

    /** The task ID, if the failure occurred while iterating on a specific task. */
    public Optional<String> taskId() {
      return taskId;
    }

    /** The iteration number, if the failure occurred while iterating on a specific task. */
    public Optional<Integer> iterationNumber() {
      return iterationNumber;
    }
  }

  /** Agent paused. The reason identifies the source (e.g. "operator"). */
  final class Paused implements LifecycleNotification {
    private final String reason;

    public Paused(String reason) {
      this.reason = reason;
    }

    /** The reason the agent was paused. */
    public String reason() {
      return reason;
    }
  }

  /** Agent resumed. The reason identifies the source (e.g. "operator"). */
  final class Resumed implements LifecycleNotification {
    private final String reason;

    public Resumed(String reason) {
      this.reason = reason;
    }

    /** The reason the agent was resumed. */
    public String reason() {
      return reason;
    }
  }

  /**
   * Agent stopped. The reason distinguishes "operator" (explicit stop) from "auto-stopped" (queue
   * drained).
   */
  final class Stopped implements LifecycleNotification {
    private final String reason;

    public Stopped(String reason) {
      this.reason = reason;
    }

    /** The reason the agent was stopped. */
    public String reason() {
      return reason;
    }
  }

  // -- Task ------------------------------------------------------------------

  /** A task was assigned (queued) — fires before the model loop begins setup for it. */
  final class TaskAssigned implements TaskNotification {
    private final String taskId;

    public TaskAssigned(String taskId) {
      this.taskId = taskId;
    }

    /** The task ID. */
    public String taskId() {
      return taskId;
    }
  }

  /** Agent started working on a task. */
  final class TaskStarted implements TaskNotification {
    private final String taskId;
    private final String taskName;

    public TaskStarted(String taskId, String taskName) {
      this.taskId = taskId;
      this.taskName = taskName;
    }

    /** The task ID. */
    public String taskId() {
      return taskId;
    }

    /** The task name. */
    public String taskName() {
      return taskName;
    }
  }

  /** Agent's task result was rejected by a validation rule. */
  final class TaskResultRejected implements TaskNotification {
    private final String taskId;
    private final String taskName;
    private final String reason;

    public TaskResultRejected(String taskId, String taskName, String reason) {
      this.taskId = taskId;
      this.taskName = taskName;
      this.reason = reason;
    }

    /** The task ID. */
    public String taskId() {
      return taskId;
    }

    /** The task name. */
    public String taskName() {
      return taskName;
    }

    /** The rejection reason. */
    public String reason() {
      return reason;
    }
  }

  /** Agent completed a task successfully. */
  final class TaskCompleted implements TaskNotification {
    private final String taskId;
    private final String taskName;

    public TaskCompleted(String taskId, String taskName) {
      this.taskId = taskId;
      this.taskName = taskName;
    }

    /** The task ID. */
    public String taskId() {
      return taskId;
    }

    /** The task name. */
    public String taskName() {
      return taskName;
    }
  }

  /** Agent failed a task via explicit fail_task. */
  final class TaskFailed implements TaskNotification {
    private final String taskId;
    private final String taskName;
    private final String reason;

    public TaskFailed(String taskId, String taskName, String reason) {
      this.taskId = taskId;
      this.taskName = taskName;
      this.reason = reason;
    }

    /** The task ID. */
    public String taskId() {
      return taskId;
    }

    /** The task name. */
    public String taskName() {
      return taskName;
    }

    /** The failure reason. */
    public String reason() {
      return reason;
    }
  }

  /**
   * Task was cancelled by the framework — dependency failure, max iterations, orphan cleanup, etc.
   * Distinct from {@link TaskFailed}, which represents the agent's explicit decision to fail the
   * task.
   */
  final class TaskCancelled implements TaskNotification {
    private final String taskId;
    private final String taskName;
    private final String reason;

    public TaskCancelled(String taskId, String taskName, String reason) {
      this.taskId = taskId;
      this.taskName = taskName;
      this.reason = reason;
    }

    /** The task ID. */
    public String taskId() {
      return taskId;
    }

    /** The task name. */
    public String taskName() {
      return taskName;
    }

    /** The cancellation reason. */
    public String reason() {
      return reason;
    }
  }

  /** A task is blocked waiting for one or more dependency tasks to resolve. */
  final class TaskDependencyWait implements TaskNotification {
    private final String taskId;
    private final List<String> pendingDependencyTaskIds;

    public TaskDependencyWait(String taskId, List<String> pendingDependencyTaskIds) {
      this.taskId = taskId;
      this.pendingDependencyTaskIds = pendingDependencyTaskIds;
    }

    /** The task ID. */
    public String taskId() {
      return taskId;
    }

    /** The IDs of the dependency tasks that have not yet resolved. */
    public List<String> pendingDependencyTaskIds() {
      return pendingDependencyTaskIds;
    }
  }

  /** A specific dependency for a task has resolved (success or failure). */
  final class DependencyResolved implements TaskNotification {
    private final String taskId;
    private final String dependencyTaskId;
    private final boolean success;
    private final String reason;

    public DependencyResolved(
        String taskId, String dependencyTaskId, boolean success, String reason) {
      this.taskId = taskId;
      this.dependencyTaskId = dependencyTaskId;
      this.success = success;
      this.reason = reason;
    }

    /** The task ID. */
    public String taskId() {
      return taskId;
    }

    /** The ID of the dependency task that resolved. */
    public String dependencyTaskId() {
      return dependencyTaskId;
    }

    /** Whether the dependency resolved successfully. */
    public boolean success() {
      return success;
    }

    /** The resolution reason (e.g. failure description). */
    public String reason() {
      return reason;
    }
  }

  // -- Handoff ---------------------------------------------------------------

  /** Source side: this agent has handed off a task to another agent. */
  final class HandoffStarted implements HandoffNotification {
    private final String taskId;
    private final String taskName;
    private final String targetComponentId;
    private final String targetInstanceId;

    public HandoffStarted(
        String taskId, String taskName, String targetComponentId, String targetInstanceId) {
      this.taskId = taskId;
      this.taskName = taskName;
      this.targetComponentId = targetComponentId;
      this.targetInstanceId = targetInstanceId;
    }

    /** The task ID. */
    public String taskId() {
      return taskId;
    }

    /** The task name. */
    public String taskName() {
      return taskName;
    }

    /** The component ID of the target agent. */
    public String targetComponentId() {
      return targetComponentId;
    }

    /** The instance ID of the target agent. */
    public String targetInstanceId() {
      return targetInstanceId;
    }
  }

  /**
   * Target side: this agent has received a handed-off task from another agent. Only the task ID is
   * known at acceptance time — the task definition (and name) is loaded later during setup.
   */
  final class HandoffReceived implements HandoffNotification {
    private final String taskId;
    private final String sourceComponentId;
    private final String sourceInstanceId;

    public HandoffReceived(String taskId, String sourceComponentId, String sourceInstanceId) {
      this.taskId = taskId;
      this.sourceComponentId = sourceComponentId;
      this.sourceInstanceId = sourceInstanceId;
    }

    /** The task ID. */
    public String taskId() {
      return taskId;
    }

    /** The component ID of the source agent. */
    public String sourceComponentId() {
      return sourceComponentId;
    }

    /** The instance ID of the source agent. */
    public String sourceInstanceId() {
      return sourceInstanceId;
    }
  }

  // -- Delegation ------------------------------------------------------------

  /**
   * Orchestrator side: a batch of subtasks has been dispatched to workers. For request-based
   * delegations the per-subtask id lists are empty; {@link #delegationCount()} remains
   * authoritative.
   */
  final class DelegationStarted implements DelegationNotification {
    private final List<String> workerComponentIds;
    private final int delegationCount;
    private final List<String> subtaskIds;
    private final List<String> workerInstanceIds;

    public DelegationStarted(
        List<String> workerComponentIds,
        int delegationCount,
        List<String> subtaskIds,
        List<String> workerInstanceIds) {
      this.workerComponentIds = workerComponentIds;
      this.delegationCount = delegationCount;
      this.subtaskIds = subtaskIds;
      this.workerInstanceIds = workerInstanceIds;
    }

    /** The component IDs of the workers receiving the delegation. */
    public List<String> workerComponentIds() {
      return workerComponentIds;
    }

    /** The number of subtasks dispatched in this batch. */
    public int delegationCount() {
      return delegationCount;
    }

    /** The IDs of the dispatched subtasks (empty for request-based delegations). */
    public List<String> subtaskIds() {
      return subtaskIds;
    }

    /**
     * The instance IDs of the workers receiving the delegation (empty for request-based
     * delegations).
     */
    public List<String> workerInstanceIds() {
      return workerInstanceIds;
    }
  }

  /** Orchestrator side: aggregate resolution of a delegation batch. */
  final class DelegationResolved implements DelegationNotification {
    private final int succeeded;
    private final int failed;
    private final List<String> succeededSubtaskIds;
    private final List<String> failedSubtaskIds;

    public DelegationResolved(
        int succeeded,
        int failed,
        List<String> succeededSubtaskIds,
        List<String> failedSubtaskIds) {
      this.succeeded = succeeded;
      this.failed = failed;
      this.succeededSubtaskIds = succeededSubtaskIds;
      this.failedSubtaskIds = failedSubtaskIds;
    }

    /** The number of subtasks that completed successfully. */
    public int succeeded() {
      return succeeded;
    }

    /** The number of subtasks that failed. */
    public int failed() {
      return failed;
    }

    /** The IDs of subtasks that completed successfully. */
    public List<String> succeededSubtaskIds() {
      return succeededSubtaskIds;
    }

    /** The IDs of subtasks that failed. */
    public List<String> failedSubtaskIds() {
      return failedSubtaskIds;
    }
  }

  /** Worker side: this agent accepted a delegated subtask from the orchestrator. */
  final class WorkerTaskReceived implements DelegationNotification {
    private final String subtaskId;
    private final String orchestratorComponentId;
    private final String orchestratorInstanceId;

    public WorkerTaskReceived(
        String subtaskId, String orchestratorComponentId, String orchestratorInstanceId) {
      this.subtaskId = subtaskId;
      this.orchestratorComponentId = orchestratorComponentId;
      this.orchestratorInstanceId = orchestratorInstanceId;
    }

    /** The subtask ID. */
    public String subtaskId() {
      return subtaskId;
    }

    /** The component ID of the orchestrator that delegated the subtask. */
    public String orchestratorComponentId() {
      return orchestratorComponentId;
    }

    /** The instance ID of the orchestrator that delegated the subtask. */
    public String orchestratorInstanceId() {
      return orchestratorInstanceId;
    }
  }

  /** Worker side: this agent finished its delegated subtask. */
  final class WorkerTaskCompleted implements DelegationNotification {
    private final String subtaskId;

    public WorkerTaskCompleted(String subtaskId) {
      this.subtaskId = subtaskId;
    }

    /** The subtask ID. */
    public String subtaskId() {
      return subtaskId;
    }
  }

  // -- Team ------------------------------------------------------------------

  /** Team lead: a new team was formed with the given members. */
  final class TeamCreated implements TeamNotification {
    private final String teamId;
    private final List<String> memberComponentIds;
    private final List<String> memberInstanceIds;

    public TeamCreated(
        String teamId, List<String> memberComponentIds, List<String> memberInstanceIds) {
      this.teamId = teamId;
      this.memberComponentIds = memberComponentIds;
      this.memberInstanceIds = memberInstanceIds;
    }

    /** The team ID. */
    public String teamId() {
      return teamId;
    }

    /** The component IDs of the team members (parallel with {@link #memberInstanceIds()}). */
    public List<String> memberComponentIds() {
      return memberComponentIds;
    }

    /** The instance IDs of the team members (parallel with {@link #memberComponentIds()}). */
    public List<String> memberInstanceIds() {
      return memberInstanceIds;
    }
  }

  /** Team lead: a team member's setup chain completed and the member is now active. */
  final class TeamMemberReady implements TeamNotification {
    private final String teamId;
    private final String memberComponentId;
    private final String memberInstanceId;

    public TeamMemberReady(String teamId, String memberComponentId, String memberInstanceId) {
      this.teamId = teamId;
      this.memberComponentId = memberComponentId;
      this.memberInstanceId = memberInstanceId;
    }

    /** The team ID. */
    public String teamId() {
      return teamId;
    }

    /** The component ID of the member. */
    public String memberComponentId() {
      return memberComponentId;
    }

    /** The instance ID of the member. */
    public String memberInstanceId() {
      return memberInstanceId;
    }
  }

  /** Team lead: a team member's setup chain failed. */
  final class TeamMemberSetupFailed implements TeamNotification {
    private final String teamId;
    private final String memberComponentId;
    private final String memberInstanceId;
    private final String reason;

    public TeamMemberSetupFailed(
        String teamId, String memberComponentId, String memberInstanceId, String reason) {
      this.teamId = teamId;
      this.memberComponentId = memberComponentId;
      this.memberInstanceId = memberInstanceId;
      this.reason = reason;
    }

    /** The team ID. */
    public String teamId() {
      return teamId;
    }

    /** The component ID of the member. */
    public String memberComponentId() {
      return memberComponentId;
    }

    /** The instance ID of the member. */
    public String memberInstanceId() {
      return memberInstanceId;
    }

    /** The setup failure reason. */
    public String reason() {
      return reason;
    }
  }

  /** Team lead: a team member has stopped. */
  final class TeamMemberStopped implements TeamNotification {
    private final String teamId;
    private final String memberComponentId;
    private final String memberInstanceId;

    public TeamMemberStopped(String teamId, String memberComponentId, String memberInstanceId) {
      this.teamId = teamId;
      this.memberComponentId = memberComponentId;
      this.memberInstanceId = memberInstanceId;
    }

    /** The team ID. */
    public String teamId() {
      return teamId;
    }

    /** The component ID of the member. */
    public String memberComponentId() {
      return memberComponentId;
    }

    /** The instance ID of the member. */
    public String memberInstanceId() {
      return memberInstanceId;
    }
  }

  /** Team lead: a team has been disbanded. */
  final class TeamDisbanded implements TeamNotification {
    private final String teamId;

    public TeamDisbanded(String teamId) {
      this.teamId = teamId;
    }

    /** The team ID. */
    public String teamId() {
      return teamId;
    }
  }

  /** Team member: this agent joined a team led by the given agent. */
  final class TeamJoined implements TeamNotification {
    private final String leadComponentId;
    private final String leadInstanceId;

    public TeamJoined(String leadComponentId, String leadInstanceId) {
      this.leadComponentId = leadComponentId;
      this.leadInstanceId = leadInstanceId;
    }

    /** The component ID of the team lead. */
    public String leadComponentId() {
      return leadComponentId;
    }

    /** The instance ID of the team lead. */
    public String leadInstanceId() {
      return leadInstanceId;
    }
  }

  // -- Backlog ---------------------------------------------------------------

  /** Backlog management: a backlog has been assigned to this agent. */
  final class BacklogAssigned implements BacklogNotification {
    private final String backlogId;
    private final String backlogName;

    public BacklogAssigned(String backlogId, String backlogName) {
      this.backlogId = backlogId;
      this.backlogName = backlogName;
    }

    /** The backlog ID. */
    public String backlogId() {
      return backlogId;
    }

    /** The backlog name. */
    public String backlogName() {
      return backlogName;
    }
  }

  /** Backlog management: a backlog has been closed. */
  final class BacklogClosed implements BacklogNotification {
    private final String backlogId;
    private final String backlogName;

    public BacklogClosed(String backlogId, String backlogName) {
      this.backlogId = backlogId;
      this.backlogName = backlogName;
    }

    /** The backlog ID. */
    public String backlogId() {
      return backlogId;
    }

    /** The backlog name. */
    public String backlogName() {
      return backlogName;
    }
  }

  /** Backlog access: this agent has been granted access to a backlog. */
  final class BacklogAccessGranted implements BacklogNotification {
    private final String backlogId;
    private final String backlogName;

    public BacklogAccessGranted(String backlogId, String backlogName) {
      this.backlogId = backlogId;
      this.backlogName = backlogName;
    }

    /** The backlog ID. */
    public String backlogId() {
      return backlogId;
    }

    /** The backlog name. */
    public String backlogName() {
      return backlogName;
    }
  }

  /** Backlog access: this agent claimed a task from a backlog. */
  final class BacklogTaskClaimed implements BacklogNotification {
    private final String backlogId;
    private final String backlogName;
    private final String taskId;

    public BacklogTaskClaimed(String backlogId, String backlogName, String taskId) {
      this.backlogId = backlogId;
      this.backlogName = backlogName;
      this.taskId = taskId;
    }

    /** The backlog ID. */
    public String backlogId() {
      return backlogId;
    }

    /** The backlog name. */
    public String backlogName() {
      return backlogName;
    }

    /** The ID of the task that was claimed. */
    public String taskId() {
      return taskId;
    }
  }

  // -- Conversation ----------------------------------------------------------

  /** Conversation moderator: a new conversation was created with the given participants. */
  final class ConversationCreated implements ConversationNotification {
    private final String conversationId;
    private final String pattern;
    private final String topic;
    private final List<String> participantComponentIds;
    private final List<String> participantInstanceIds;

    public ConversationCreated(
        String conversationId,
        String pattern,
        String topic,
        List<String> participantComponentIds,
        List<String> participantInstanceIds) {
      this.conversationId = conversationId;
      this.pattern = pattern;
      this.topic = topic;
      this.participantComponentIds = participantComponentIds;
      this.participantInstanceIds = participantInstanceIds;
    }

    /** The conversation ID. */
    public String conversationId() {
      return conversationId;
    }

    /** The conversation pattern. */
    public String pattern() {
      return pattern;
    }

    /** The conversation topic. */
    public String topic() {
      return topic;
    }

    /** The component IDs of the participants (parallel with {@link #participantInstanceIds()}). */
    public List<String> participantComponentIds() {
      return participantComponentIds;
    }

    /** The instance IDs of the participants (parallel with {@link #participantComponentIds()}). */
    public List<String> participantInstanceIds() {
      return participantInstanceIds;
    }
  }

  /** Conversation moderator: a participant's setup completed and they are ready to take turns. */
  final class ConversationParticipantReady implements ConversationNotification {
    private final String conversationId;
    private final String participantComponentId;
    private final String participantInstanceId;

    public ConversationParticipantReady(
        String conversationId, String participantComponentId, String participantInstanceId) {
      this.conversationId = conversationId;
      this.participantComponentId = participantComponentId;
      this.participantInstanceId = participantInstanceId;
    }

    /** The conversation ID. */
    public String conversationId() {
      return conversationId;
    }

    /** The component ID of the participant. */
    public String participantComponentId() {
      return participantComponentId;
    }

    /** The instance ID of the participant. */
    public String participantInstanceId() {
      return participantInstanceId;
    }
  }

  /** Conversation moderator: a participant's setup failed. */
  final class ConversationParticipantSetupFailed implements ConversationNotification {
    private final String conversationId;
    private final String participantComponentId;
    private final String participantInstanceId;
    private final String reason;

    public ConversationParticipantSetupFailed(
        String conversationId,
        String participantComponentId,
        String participantInstanceId,
        String reason) {
      this.conversationId = conversationId;
      this.participantComponentId = participantComponentId;
      this.participantInstanceId = participantInstanceId;
      this.reason = reason;
    }

    /** The conversation ID. */
    public String conversationId() {
      return conversationId;
    }

    /** The component ID of the participant. */
    public String participantComponentId() {
      return participantComponentId;
    }

    /** The instance ID of the participant. */
    public String participantInstanceId() {
      return participantInstanceId;
    }

    /** The setup failure reason. */
    public String reason() {
      return reason;
    }
  }

  /** Conversation moderator: a conversation has ended. */
  final class ConversationEnded implements ConversationNotification {
    private final String conversationId;

    public ConversationEnded(String conversationId) {
      this.conversationId = conversationId;
    }

    /** The conversation ID. */
    public String conversationId() {
      return conversationId;
    }
  }

  /** Conversation moderator: a turn was received from a participant. */
  final class ConversationTurnReceived implements ConversationNotification {
    private final String conversationId;
    private final String participantComponentId;
    private final String participantInstanceId;

    public ConversationTurnReceived(
        String conversationId, String participantComponentId, String participantInstanceId) {
      this.conversationId = conversationId;
      this.participantComponentId = participantComponentId;
      this.participantInstanceId = participantInstanceId;
    }

    /** The conversation ID. */
    public String conversationId() {
      return conversationId;
    }

    /** The component ID of the participant whose turn was received. */
    public String participantComponentId() {
      return participantComponentId;
    }

    /** The instance ID of the participant whose turn was received. */
    public String participantInstanceId() {
      return participantInstanceId;
    }
  }

  /** Conversation participant: this agent joined a conversation moderated by the given agent. */
  final class ConversationJoined implements ConversationNotification {
    private final String conversationId;
    private final String moderatorComponentId;
    private final String moderatorInstanceId;

    public ConversationJoined(
        String conversationId, String moderatorComponentId, String moderatorInstanceId) {
      this.conversationId = conversationId;
      this.moderatorComponentId = moderatorComponentId;
      this.moderatorInstanceId = moderatorInstanceId;
    }

    /** The conversation ID. */
    public String conversationId() {
      return conversationId;
    }

    /** The component ID of the moderator. */
    public String moderatorComponentId() {
      return moderatorComponentId;
    }

    /** The instance ID of the moderator. */
    public String moderatorInstanceId() {
      return moderatorInstanceId;
    }
  }

  /**
   * Conversation participant: this agent submitted its turn after the given number of iterations.
   */
  final class ParticipantTurnSubmitted implements ConversationNotification {
    private final String conversationId;
    private final int iterationsUsed;

    public ParticipantTurnSubmitted(String conversationId, int iterationsUsed) {
      this.conversationId = conversationId;
      this.iterationsUsed = iterationsUsed;
    }

    /** The conversation ID. */
    public String conversationId() {
      return conversationId;
    }

    /** The number of iterations used to produce the turn. */
    public int iterationsUsed() {
      return iterationsUsed;
    }
  }

  // -- Messaging -------------------------------------------------------------

  /** A message was received from another agent. */
  final class MessageReceived implements MessagingNotification {
    private final String fromComponentId;
    private final String fromInstanceId;
    private final String text;

    public MessageReceived(String fromComponentId, String fromInstanceId, String text) {
      this.fromComponentId = fromComponentId;
      this.fromInstanceId = fromInstanceId;
      this.text = text;
    }

    /** The component ID of the sending agent. */
    public String fromComponentId() {
      return fromComponentId;
    }

    /** The instance ID of the sending agent. */
    public String fromInstanceId() {
      return fromInstanceId;
    }

    /** The message text. */
    public String text() {
      return text;
    }
  }

  /** A new contact was introduced to this agent. */
  final class ContactAdded implements MessagingNotification {
    private final String contactComponentId;
    private final String contactInstanceId;

    public ContactAdded(String contactComponentId, String contactInstanceId) {
      this.contactComponentId = contactComponentId;
      this.contactInstanceId = contactInstanceId;
    }

    /** The component ID of the contact. */
    public String contactComponentId() {
      return contactComponentId;
    }

    /** The instance ID of the contact. */
    public String contactInstanceId() {
      return contactInstanceId;
    }
  }

  // -- Struggle signals ------------------------------------------------------

  /**
   * Derived signal: a task is struggling — N consecutive iteration failures, M consecutive result
   * rejections, or some other impediment captured in {@link #reason()}. Fires once per detection;
   * the underlying counters reset on task termination or a successful recovery.
   */
  final class TaskStruggleDetected implements StruggleNotification {
    private final String taskId;
    private final String taskName;
    private final String reason;
    private final int iteration;
    private final int maxIterations;

    public TaskStruggleDetected(
        String taskId, String taskName, String reason, int iteration, int maxIterations) {
      this.taskId = taskId;
      this.taskName = taskName;
      this.reason = reason;
      this.iteration = iteration;
      this.maxIterations = maxIterations;
    }

    /** The task ID. */
    public String taskId() {
      return taskId;
    }

    /** The task name. */
    public String taskName() {
      return taskName;
    }

    /** Which condition tripped the struggle detector. */
    public String reason() {
      return reason;
    }

    /** The current iteration count. */
    public int iteration() {
      return iteration;
    }

    /** The configured max iterations for the task. */
    public int maxIterations() {
      return maxIterations;
    }
  }

  /**
   * Derived signal: a task waiting on dependencies has been waiting longer than the configured
   * threshold. Re-fires periodically until the dependency resolves.
   */
  final class TaskDependencyStuck implements StruggleNotification {
    private final String taskId;
    private final List<String> pendingDependencyTaskIds;
    private final long waitDurationSeconds;

    public TaskDependencyStuck(
        String taskId, List<String> pendingDependencyTaskIds, long waitDurationSeconds) {
      this.taskId = taskId;
      this.pendingDependencyTaskIds = pendingDependencyTaskIds;
      this.waitDurationSeconds = waitDurationSeconds;
    }

    /** The task ID. */
    public String taskId() {
      return taskId;
    }

    /** The IDs of the dependency tasks that have not yet resolved. */
    public List<String> pendingDependencyTaskIds() {
      return pendingDependencyTaskIds;
    }

    /** How long the task has been waiting on its dependencies, in seconds. */
    public long waitDurationSeconds() {
      return waitDurationSeconds;
    }
  }

  /**
   * Derived signal: a task is at or beyond a configurable fraction (default 80%) of its max
   * iterations. Useful as a heads-up before the framework cancels with "max iterations".
   */
  final class TaskApproachingMaxIterations implements StruggleNotification {
    private final String taskId;
    private final String taskName;
    private final int iteration;
    private final int maxIterations;

    public TaskApproachingMaxIterations(
        String taskId, String taskName, int iteration, int maxIterations) {
      this.taskId = taskId;
      this.taskName = taskName;
      this.iteration = iteration;
      this.maxIterations = maxIterations;
    }

    /** The task ID. */
    public String taskId() {
      return taskId;
    }

    /** The task name. */
    public String taskName() {
      return taskName;
    }

    /** The current iteration count. */
    public int iteration() {
      return iteration;
    }

    /** The configured max iterations for the task. */
    public int maxIterations() {
      return maxIterations;
    }
  }

  /**
   * Derived signal: N consecutive iteration failures in flows not tied to a specific task (pre-task
   * setup, request-based delegation). Same threshold as {@link TaskStruggleDetected} for iteration
   * failures, surfaced separately so subscribers don't have to disambiguate.
   */
  final class RepeatedIterationFailure implements StruggleNotification {
    private final int iterationsFailed;
    private final String lastReason;

    public RepeatedIterationFailure(int iterationsFailed, String lastReason) {
      this.iterationsFailed = iterationsFailed;
      this.lastReason = lastReason;
    }

    /** The number of consecutive iteration failures. */
    public int iterationsFailed() {
      return iterationsFailed;
    }

    /** The reason from the most recent failure. */
    public String lastReason() {
      return lastReason;
    }
  }
}
