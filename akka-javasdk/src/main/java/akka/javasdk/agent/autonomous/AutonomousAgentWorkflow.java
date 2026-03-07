/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.autonomous;

import static java.time.Duration.ofSeconds;

import akka.Done;
import akka.javasdk.agent.task.PolicyResult;
import akka.javasdk.agent.task.TaskAssignmentContext;
import akka.javasdk.agent.task.TaskEntity;
import akka.javasdk.agent.task.TaskPolicy;
import akka.javasdk.agent.task.TaskState;
import akka.javasdk.agent.task.TaskStatus;
import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.workflow.Workflow;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provided Workflow component that implements the autonomous agent execution loop.
 *
 * <p>Supports sequential multi-task processing: the agent runs a loop, processes one task at a
 * time, and when done either picks up the next pending task or pauses waiting for more work.
 *
 * <p>Each iteration fires the LLM agent call asynchronously via a timer bridge, then pauses. While
 * paused the workflow is idle and can process commands (disband, stop, assign task, etc.). When the
 * LLM completes, the bridge delivers the result back as a command, and the workflow resumes.
 */
@Component(
    id = "akka-autonomous-agent",
    name = "AutonomousAgentWorkflow",
    description = "Execution loop for autonomous agents")
public final class AutonomousAgentWorkflow extends Workflow<AutonomousAgentState> {

  private static final Logger log = LoggerFactory.getLogger(AutonomousAgentWorkflow.class);

  private final ComponentClient componentClient;

  public AutonomousAgentWorkflow(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  /** Request to start the autonomous agent (configures the strategy, no task yet). */
  public record StartRequest(
      int maxIterationsPerTask,
      String instructions,
      List<String> toolClassNames,
      List<Capability> capabilities,
      String contentLoaderClassName) {}

  /** Delivery payload from the IterationBridgeAction after the LLM completes. */
  public record IterationResult(int iteration, boolean teamMember) {}

  @Override
  public WorkflowSettings settings() {
    // iterateStep is now fast (pre-checks + timer scheduling only), no LLM blocking
    return WorkflowSettings.builder().defaultStepTimeout(ofSeconds(30)).build();
  }

  @Override
  public AutonomousAgentState emptyState() {
    return null;
  }

  // ── Command handlers ──────────────────────────────────────────────────────

  /**
   * Start the agent with its strategy configuration. Idempotent — safe to call if already started.
   */
  public Effect<Done> start(StartRequest request) {
    if (currentState() != null) {
      return effects().reply(Done.done()); // already started, idempotent
    }

    var workflowId = commandContext().workflowId();
    var initialState =
        AutonomousAgentState.initial(
            workflowId,
            request.maxIterationsPerTask(),
            request.instructions(),
            request.toolClassNames(),
            request.capabilities(),
            request.contentLoaderClassName());

    return effects().updateState(initialState).pause().thenReply(Done.done());
  }

  /** Assign a task to this agent. Resumes the agent if idle, queues the task if busy. */
  public Effect<Done> assignTask(String taskId) {
    return doAssignTask(taskId, false);
  }

  /** Assign a single task. The agent will stop automatically when it completes. */
  public Effect<Done> assignSingleTask(String taskId) {
    return doAssignTask(taskId, true);
  }

  /**
   * Assign a task that was handed off from another agent. The task is already IN_PROGRESS — does
   * not call TaskEntity assign or start. The agent will stop when done.
   */
  public Effect<Done> assignHandedOffTask(String taskId) {
    if (currentState() == null) {
      return effects().error("Autonomous agent not started");
    }

    var updatedState = currentState().addTask(taskId).withStopWhenDone();

    if (!updatedState.hasCurrentTask()) {
      updatedState = updatedState.startNextTask();
    }

    if (currentState().status() == AutonomousAgentState.Status.AWAITING_LLM) {
      // LLM call in flight — queue the task, delivery callback will pick it up
      return effects().updateState(updatedState).pause().thenReply(Done.done());
    }

    return effects()
        .updateState(updatedState)
        .transitionTo(AutonomousAgentWorkflow::iterateStep)
        .thenReply(Done.done());
  }

  private Effect<Done> doAssignTask(String taskId, boolean stopWhenDone) {
    if (currentState() == null) {
      return effects().error("Autonomous agent not started");
    }

    var agentId = currentState().sessionId();

    // Evaluate assignment policies before assigning
    TaskState taskState =
        componentClient.forEventSourcedEntity(taskId).method(TaskEntity::getState).invoke();
    var policyClassNames = taskState.policyClassNames();
    if (policyClassNames != null && !policyClassNames.isEmpty()) {
      var ctx =
          new TaskAssignmentContext(taskState.description(), taskState.instructions(), agentId);
      for (String policyClassName : policyClassNames) {
        try {
          var policy = instantiatePolicy(policyClassName);
          var policyResult = policy.onAssignment(ctx);
          switch (policyResult) {
            case PolicyResult.Deny deny -> {
              return effects().error("Assignment denied by policy: " + deny.reason());
            }
            case PolicyResult.RequireApproval approval -> {
              return effects()
                  .error("Assignment denied (approval not supported): " + approval.reason());
            }
            case PolicyResult.Allow allow -> {
              // continue to next policy
            }
          }
        } catch (Exception e) {
          log.error("Failed to evaluate assignment policy {}", policyClassName, e);
          return effects().error("Assignment policy evaluation error: " + e.getMessage());
        }
      }
    }

    // Assign and start the task
    componentClient.forEventSourcedEntity(taskId).method(TaskEntity::assign).invoke(agentId);
    componentClient.forEventSourcedEntity(taskId).method(TaskEntity::start).invoke();

    var updatedState = currentState().addTask(taskId);
    if (stopWhenDone) {
      updatedState = updatedState.withStopWhenDone();
    }

    if (!updatedState.hasCurrentTask()) {
      // Agent is idle — pick up this task and resume processing
      updatedState = updatedState.startNextTask();
    }

    if (currentState().status() == AutonomousAgentState.Status.AWAITING_LLM) {
      // LLM call in flight — queue the task, delivery callback will pick it up
      return effects().updateState(updatedState).pause().thenReply(Done.done());
    }

    return effects()
        .updateState(updatedState)
        .transitionTo(AutonomousAgentWorkflow::iterateStep)
        .thenReply(Done.done());
  }

  /**
   * Start this agent as a team member. The agent iterates without a dedicated task — it works from
   * the shared task list and collaborates via messaging until disbanded by the team lead.
   */
  public record TeamMemberStartRequest(
      int maxIterationsPerTask,
      String instructions,
      List<String> toolClassNames,
      List<Capability> capabilities,
      String contentLoaderClassName) {}

  public Effect<Done> startAsTeamMember(TeamMemberStartRequest request) {
    if (currentState() != null) {
      return effects().reply(Done.done()); // idempotent
    }

    var workflowId = commandContext().workflowId();

    var initialState =
        AutonomousAgentState.initial(
                workflowId,
                request.maxIterationsPerTask(),
                request.instructions(),
                request.toolClassNames(),
                request.capabilities(),
                request.contentLoaderClassName())
            .withTeamMember();

    return effects()
        .updateState(initialState)
        .transitionTo(AutonomousAgentWorkflow::iterateStep)
        .thenReply(Done.done());
  }

  /**
   * Build and return the ExecuteRequest for the current iteration. Called by IterationBridgeAction
   * to fetch the full request data without passing it through the timer payload.
   */
  public Effect<StrategyExecutor.ExecuteRequest> getExecuteRequest() {
    var state = currentState();
    if (state == null) {
      return effects().error("Autonomous agent not started");
    }

    if (state.teamMember()) {
      // Team member — no dedicated task
      var request =
          new StrategyExecutor.ExecuteRequest(
              null,
              null,
              null,
              state.currentTaskIterationCount(),
              state.maxIterationsPerTask(),
              state.instructions(),
              state.toolClassNames(),
              commandContext().workflowId(),
              state.capabilities(),
              null,
              null,
              null,
              state.contentLoaderClassName(),
              List.of());
      return effects().reply(request);
    }

    var taskId = state.currentTaskId();
    if (taskId == null) {
      return effects().error("No current task");
    }

    TaskState taskState =
        componentClient.forEventSourcedEntity(taskId).method(TaskEntity::getState).invoke();

    var request =
        new StrategyExecutor.ExecuteRequest(
            taskId,
            taskState.description(),
            taskState.instructions(),
            state.currentTaskIterationCount(),
            state.maxIterationsPerTask(),
            state.instructions(),
            state.toolClassNames(),
            commandContext().workflowId(),
            state.capabilities(),
            taskState.resultTypeName(),
            taskState.handoffContext(),
            taskState.contentRefs(),
            state.contentLoaderClassName(),
            taskState.policyClassNames());
    return effects().reply(request);
  }

  /**
   * Resume the agent after a task approval or rejection. Called by TaskClient after calling
   * approve() or reject() on the TaskEntity.
   */
  public Effect<Done> resumeAfterApproval() {
    if (currentState() == null) {
      return effects().error("Autonomous agent not started");
    }
    if (!currentState().hasCurrentTask()) {
      return effects().error("No current task to resume");
    }
    log.info(
        "Autonomous agent {} resuming after approval for task {}",
        currentState().sessionId(),
        currentState().currentTaskId());
    // Read updated task state to determine next step (approved → completed, rejected → failed)
    var taskId = currentState().currentTaskId();
    TaskState taskState =
        componentClient.forEventSourcedEntity(taskId).method(TaskEntity::getState).invoke();

    if (taskState.status() == TaskStatus.COMPLETED || taskState.status() == TaskStatus.FAILED) {
      return moveToNextTaskFromCommand(currentState());
    }
    // Still in progress — continue iterating
    return effects().transitionTo(AutonomousAgentWorkflow::iterateStep).thenReply(Done.done());
  }

  /**
   * Retry dependency check after a delay. Called by a timer when a task was re-queued because its
   * dependencies weren't ready.
   */
  public Effect<Done> retryDependencyCheck() {
    if (currentState() == null) {
      return effects().error("Autonomous agent not started");
    }
    if (currentState().hasPendingTasks() && !currentState().hasCurrentTask()) {
      var nextState = currentState().startNextTask();
      return effects()
          .updateState(nextState)
          .transitionTo(AutonomousAgentWorkflow::iterateStep)
          .thenReply(Done.done());
    }
    if (currentState().hasPendingTasks()) {
      return effects().transitionTo(AutonomousAgentWorkflow::iterateStep).thenReply(Done.done());
    }
    return effects().reply(Done.done());
  }

  /**
   * Stop the agent, ending the workflow. Cancels any claimed tasks on shared task lists and any
   * in-flight LLM timer.
   */
  public Effect<Done> stop() {
    if (currentState() == null) {
      return effects().error("Autonomous agent not started");
    }
    var state = currentState();
    log.info("Autonomous agent {} stopping", state.sessionId());

    // Cancel any in-flight LLM timer
    if (state.status() == AutonomousAgentState.Status.AWAITING_LLM) {
      var workflowId = commandContext().workflowId();
      timers().delete("llm-" + workflowId + "-" + state.awaitingIteration());
    }

    cancelClaimedTasks(state);
    return effects().updateState(state.withStopped()).end().thenReply(Done.done());
  }

  /**
   * Disband this team member, signalling it should stop. With the async pattern, the workflow is
   * paused while the LLM runs, so this command is processed immediately.
   */
  public Effect<Done> disband() {
    if (currentState() == null) {
      log.info("disband() called but workflow has no state (never started)");
      return effects().reply(Done.done());
    }
    var state = currentState();
    log.info(
        "disband() command processing for {} — status={}, disbanded={}, teamMember={},"
            + " iteration={}, hasCurrentTask={}",
        state.sessionId(),
        state.status(),
        state.disbanded(),
        state.teamMember(),
        state.currentTaskIterationCount(),
        state.hasCurrentTask());

    if (state.status() == AutonomousAgentState.Status.STOPPED) {
      log.info("disband() — agent {} already STOPPED, no-op", state.sessionId());
      return effects().reply(Done.done());
    }
    if (state.disbanded()) {
      log.info("disband() — agent {} already disbanded, no-op", state.sessionId());
      return effects().reply(Done.done());
    }

    log.info("disband() — setting disbanded flag for agent {}", state.sessionId());
    var updatedState = state.withDisbanded();

    if (state.status() == AutonomousAgentState.Status.AWAITING_LLM) {
      // LLM call is in flight — just set the flag and stay paused.
      // deliverIterationResult will see the flag when the LLM completes.
      return effects().updateState(updatedState).pause().thenReply(Done.done());
    }

    // Not awaiting LLM — transition to iterateStep to process the disband
    return effects()
        .updateState(updatedState)
        .transitionTo(AutonomousAgentWorkflow::iterateStep)
        .thenReply(Done.done());
  }

  /**
   * Receive the result of an async LLM iteration. Called by IterationBridgeAction after the
   * StrategyExecutor completes. All post-LLM logic runs here.
   */
  public Effect<Done> deliverIterationResult(IterationResult result) {
    if (currentState() == null) {
      return effects().error("Autonomous agent not started");
    }

    var state = currentState();

    // Stale delivery guard
    if (state.awaitingIteration() != result.iteration()) {
      log.info(
          "Ignoring stale delivery for iteration {} (expecting {})",
          result.iteration(),
          state.awaitingIteration());
      return effects().reply(Done.done());
    }

    // Already stopped while we were waiting
    if (state.status() == AutonomousAgentState.Status.STOPPED) {
      log.info("deliverIterationResult — agent {} already stopped", state.sessionId());
      return effects().reply(Done.done());
    }

    // Team member path
    if (result.teamMember()) {
      return deliverTeamMemberResult(state);
    }

    // Regular task agent path
    return deliverTaskResult(state, result.iteration());
  }

  // ── Steps ─────────────────────────────────────────────────────────────────

  /**
   * The main execution step. Performs pre-LLM checks, then fires the LLM call asynchronously via
   * the timer bridge and pauses. The workflow is idle (command-ready) while the LLM runs.
   */
  private StepEffect iterateStep() {
    var state = currentState();

    // Team member path
    if (state.teamMember()) {
      return fireTeamMemberIteration(state);
    }

    if (!state.hasCurrentTask()) {
      return stepEffects().updateState(state).thenPause();
    }

    var taskId = state.currentTaskId();

    // Check if we've exceeded max iterations for this task
    if (!state.hasIterationsRemaining()) {
      log.warn(
          "Autonomous agent {} exceeded max iterations ({}) for task {}",
          state.sessionId(),
          state.maxIterationsPerTask(),
          taskId);

      componentClient
          .forEventSourcedEntity(taskId)
          .method(TaskEntity::fail)
          .invoke("Max iterations exceeded");

      return moveToNextTask(state);
    }

    // Read current task state
    TaskState taskState =
        componentClient.forEventSourcedEntity(taskId).method(TaskEntity::getState).invoke();

    // If task already completed or failed (by a tool call in a previous iteration)
    if (taskState.status() == TaskStatus.COMPLETED || taskState.status() == TaskStatus.FAILED) {
      log.info("Autonomous agent {} task {} is {}", state.sessionId(), taskId, taskState.status());
      return moveToNextTask(state);
    }

    // Check task dependencies — all must be COMPLETED before we can proceed
    var deps = taskState.dependencyTaskIds();
    if (deps != null && !deps.isEmpty()) {
      for (String depId : deps) {
        TaskState depState;
        try {
          depState =
              componentClient.forEventSourcedEntity(depId).method(TaskEntity::getState).invoke();
        } catch (Exception e) {
          log.warn(
              "Autonomous agent {} could not read dependency {} for task {}",
              state.sessionId(),
              depId,
              taskId);
          return requeueAndRetry(state);
        }

        if (depState.status() == TaskStatus.FAILED) {
          log.info(
              "Autonomous agent {} task {} dependency {} failed — failing task",
              state.sessionId(),
              taskId,
              depId);
          componentClient
              .forEventSourcedEntity(taskId)
              .method(TaskEntity::fail)
              .invoke("Dependency task " + depId + " failed");
          return moveToNextTask(state);
        }

        if (depState.status() != TaskStatus.COMPLETED) {
          log.info(
              "Autonomous agent {} task {} dependency {} not ready ({}), re-queuing",
              state.sessionId(),
              taskId,
              depId,
              depState.status());
          return requeueAndRetry(state);
        }
      }
      // All dependencies satisfied — continue to LLM iteration
    }

    var iteration = state.currentTaskIterationCount() + 1;
    log.info("Autonomous agent {} iteration {} for task {}", state.sessionId(), iteration, taskId);

    // Fire the LLM call asynchronously via the timer bridge — small correlation payload only.
    // The bridge reads the full ExecuteRequest from the workflow via getExecuteRequest().
    var workflowId = commandContext().workflowId();
    var bridgeRequest =
        new IterationBridgeAction.BridgeRequest(workflowId, state.sessionId(), iteration, false);

    timers()
        .createSingleTimer(
            "llm-" + workflowId + "-" + iteration,
            Duration.ZERO,
            componentClient
                .forTimedAction()
                .method(IterationBridgeAction::executeAndDeliver)
                .deferred(bridgeRequest));

    // Pause — the workflow is now idle and can process commands (disband, stop, etc.)
    var updatedState = state.incrementIteration().withAwaitingLlm(iteration);
    return stepEffects().updateState(updatedState).thenPause();
  }

  /**
   * Team member iteration — performs pre-checks, then fires the LLM call asynchronously and pauses.
   */
  private StepEffect fireTeamMemberIteration(AutonomousAgentState state) {
    // Check if the team has been disbanded — the flag is reliable because the workflow
    // is paused (idle) while the LLM runs, so disband() commands are processed immediately.
    if (state.disbanded()) {
      log.info("Team member {} team disbanded, cleaning up and stopping", state.sessionId());
      cancelClaimedTasks(state);
      return stepEffects().updateState(state.withStopped()).thenEnd();
    }

    // Safety bound — max iterations still applies
    if (!state.hasIterationsRemaining()) {
      log.warn(
          "Team member {} exceeded max iterations ({}), stopping",
          state.sessionId(),
          state.maxIterationsPerTask());
      return stepEffects().updateState(state.withStopped()).thenEnd();
    }

    var iteration = state.currentTaskIterationCount() + 1;
    log.info("Team member {} iteration {}", state.sessionId(), iteration);

    // Fire the LLM call asynchronously via the timer bridge — small correlation payload only.
    // The bridge reads the full ExecuteRequest from the workflow via getExecuteRequest().
    var workflowId = commandContext().workflowId();
    var bridgeRequest =
        new IterationBridgeAction.BridgeRequest(workflowId, state.sessionId(), iteration, true);

    timers()
        .createSingleTimer(
            "llm-" + workflowId + "-" + iteration,
            Duration.ZERO,
            componentClient
                .forTimedAction()
                .method(IterationBridgeAction::executeAndDeliver)
                .deferred(bridgeRequest));

    // Pause — the workflow is now idle and can process commands (disband, stop, etc.)
    var updatedState = state.incrementIteration().withAwaitingLlm(iteration);
    return stepEffects().updateState(updatedState).thenPause();
  }

  /** Recovery step — iterateStep is now fast (no LLM call), so recovery is simple: just retry. */
  private StepEffect recoverStep() {
    var state = currentState();
    log.info("Autonomous agent {} recovering from step failure", state.sessionId());

    // If team member is disbanded, just stop
    if (state.teamMember() && state.disbanded()) {
      cancelClaimedTasks(state);
      return stepEffects().updateState(state.withStopped()).thenEnd();
    }

    // Retry the step
    return stepEffects().updateState(state).thenTransitionTo(AutonomousAgentWorkflow::iterateStep);
  }

  // ── Post-LLM result handlers (called from deliverIterationResult) ─────────

  private Effect<Done> deliverTaskResult(AutonomousAgentState state, int iteration) {
    var taskId = state.currentTaskId();

    if (taskId == null) {
      log.warn("deliverTaskResult but no current task for {}", state.sessionId());
      return effects().reply(Done.done());
    }

    // Read updated task state — the LLM's tool calls may have completed/failed it
    TaskState updatedTaskState =
        componentClient.forEventSourcedEntity(taskId).method(TaskEntity::getState).invoke();

    if (updatedTaskState.status() == TaskStatus.COMPLETED
        || updatedTaskState.status() == TaskStatus.FAILED) {
      log.info(
          "Autonomous agent {} task {} is {} after iteration {}",
          state.sessionId(),
          taskId,
          updatedTaskState.status(),
          iteration);
      // Auto-disband any team this agent leads, in case the LLM forgot
      autoDisbandIfTeamLead(state);
      return moveToNextTaskFromCommand(state);
    }

    // Check if a policy requires external approval
    if (updatedTaskState.status() == TaskStatus.AWAITING_APPROVAL) {
      log.info(
          "Autonomous agent {} task {} awaiting approval: {}",
          state.sessionId(),
          taskId,
          updatedTaskState.approvalReason());
      return effects().updateState(state).pause().thenReply(Done.done());
    }

    // Check if the task was handed off (assignee changed)
    if (!state.sessionId().equals(updatedTaskState.assignee())) {
      log.info(
          "Autonomous agent {} task {} was handed off to {}",
          state.sessionId(),
          taskId,
          updatedTaskState.assignee());
      return moveToNextTaskFromCommand(state);
    }

    // Task still in progress — loop for another iteration
    log.info(
        "Autonomous agent {} task {} still in progress after iteration {}, continuing",
        state.sessionId(),
        taskId,
        iteration);
    return effects().transitionTo(AutonomousAgentWorkflow::iterateStep).thenReply(Done.done());
  }

  private Effect<Done> deliverTeamMemberResult(AutonomousAgentState state) {
    // Check disbanded — the flag is reliable with the async pattern
    if (state.disbanded()) {
      log.info("Team member {} disbanded, cleaning up and stopping", state.sessionId());
      cancelClaimedTasks(state);
      return effects().updateState(state.withStopped()).end().thenReply(Done.done());
    }

    // Continue iterating
    return effects().transitionTo(AutonomousAgentWorkflow::iterateStep).thenReply(Done.done());
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  /**
   * Move to next task from a command handler context. Same logic as moveToNextTask but uses the
   * command Effect API instead of StepEffect.
   */
  private Effect<Done> moveToNextTaskFromCommand(AutonomousAgentState state) {
    var doneState = state.taskDone();

    if (doneState.hasPendingTasks()) {
      var nextState = doneState.startNextTask();
      log.info(
          "Autonomous agent {} moving to next task {}",
          nextState.sessionId(),
          nextState.currentTaskId());
      return effects()
          .updateState(nextState)
          .transitionTo(AutonomousAgentWorkflow::iterateStep)
          .thenReply(Done.done());
    } else if (doneState.stopWhenDone()) {
      log.info("Autonomous agent {} completed single task, stopping", doneState.sessionId());
      return effects().updateState(doneState.withStopped()).end().thenReply(Done.done());
    } else {
      log.info("Autonomous agent {} has no more tasks, pausing", doneState.sessionId());
      return effects().updateState(doneState).pause().thenReply(Done.done());
    }
  }

  /** Cancel any tasks this agent has claimed on shared task lists. */
  private void cancelClaimedTasks(AutonomousAgentState state) {
    var taskListConfigs =
        state.capabilities().stream()
            .filter(c -> c instanceof TaskListCapability)
            .map(c -> (TaskListCapability) c)
            .toList();
    for (var tlc : taskListConfigs) {
      try {
        var taskListState =
            componentClient
                .forEventSourcedEntity(tlc.taskListId())
                .method(TaskListEntity::getState)
                .invoke();
        for (var task : taskListState.tasks()) {
          if (task.status() == TaskListState.TaskListItemStatus.CLAIMED
              && state.sessionId().equals(task.claimedBy())) {
            componentClient
                .forEventSourcedEntity(tlc.taskListId())
                .method(TaskListEntity::cancelTask)
                .invoke(task.taskId());
            log.info("Cancelled claimed task {} for agent {}", task.taskId(), state.sessionId());
          }
        }
      } catch (Exception e) {
        log.debug("No task list cleanup needed for {}", state.sessionId());
      }
    }
  }

  /**
   * If this agent leads a team, stop all team members. Called automatically when the lead's task
   * completes or fails to prevent orphaned team members.
   */
  private void autoDisbandIfTeamLead(AutonomousAgentState state) {
    var hasTeam = state.capabilities().stream().anyMatch(c -> c instanceof TeamCapability);
    if (!hasTeam) return;

    var teamId = state.sessionId();
    try {
      var teamState =
          componentClient.forEventSourcedEntity(teamId).method(TeamEntity::getState).invoke();
      if (teamState.members().isEmpty()) return;

      for (var member : teamState.members()) {
        componentClient
            .forWorkflow(member.agentId())
            .method(AutonomousAgentWorkflow::disband)
            .invokeAsync()
            .whenComplete(
                (result, e) -> {
                  if (e != null) {
                    log.warn(
                        "Failed to auto-disband member {}: {}", member.agentId(), e.getMessage());
                  } else {
                    log.info("Auto-disband command accepted by member {}", member.agentId());
                  }
                });
      }
      log.info("Auto-disbanded team {} ({} members)", teamId, teamState.members().size());
    } catch (Exception e) {
      log.debug("No team to auto-disband for {}", teamId);
    }
  }

  /**
   * Re-queue the current task because its dependencies aren't ready. If there are other pending
   * tasks, try the next one immediately. Otherwise, pause and schedule a retry timer.
   */
  private StepEffect requeueAndRetry(AutonomousAgentState state) {
    var requeued = state.requeueCurrentTask();

    if (requeued.hasPendingTasks()) {
      // Try the next task — maybe it has no unmet dependencies
      var nextState = requeued.startNextTask();
      return stepEffects()
          .updateState(nextState)
          .thenTransitionTo(AutonomousAgentWorkflow::iterateStep);
    } else {
      // No tasks to try — pause and schedule a retry
      var workflowId = commandContext().workflowId();
      timers()
          .createSingleTimer(
              "dep-retry-" + workflowId,
              ofSeconds(10),
              componentClient
                  .forWorkflow(workflowId)
                  .method(AutonomousAgentWorkflow::retryDependencyCheck)
                  .deferred());
      return stepEffects().updateState(requeued).thenPause();
    }
  }

  /** Instantiate a policy class using the same pattern as tool classes. */
  private TaskPolicy<?> instantiatePolicy(String className) throws Exception {
    var clz = Class.forName(className).asSubclass(TaskPolicy.class);
    try {
      var ctor = clz.getDeclaredConstructor(ComponentClient.class);
      return ctor.newInstance(componentClient);
    } catch (NoSuchMethodException e) {
      return clz.getDeclaredConstructor().newInstance();
    }
  }

  /** Move to the next pending task, stop if stopWhenDone, or pause if the queue is empty. */
  private StepEffect moveToNextTask(AutonomousAgentState state) {
    var doneState = state.taskDone();

    if (doneState.hasPendingTasks()) {
      var nextState = doneState.startNextTask();
      log.info(
          "Autonomous agent {} moving to next task {}",
          nextState.sessionId(),
          nextState.currentTaskId());
      return stepEffects()
          .updateState(nextState)
          .thenTransitionTo(AutonomousAgentWorkflow::iterateStep);
    } else if (doneState.stopWhenDone()) {
      log.info("Autonomous agent {} completed single task, stopping", doneState.sessionId());
      return stepEffects().updateState(doneState.withStopped()).thenEnd();
    } else {
      log.info("Autonomous agent {} has no more tasks, pausing", doneState.sessionId());
      return stepEffects().updateState(doneState).thenPause();
    }
  }
}
