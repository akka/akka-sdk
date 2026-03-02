/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.autonomous;

import java.util.ArrayList;
import java.util.List;

/** Internal workflow state for an autonomous agent's execution loop. */
public record AutonomousAgentState(
    String sessionId,
    int maxIterationsPerTask,
    String instructions,
    List<String> toolClassNames,
    List<Capability> capabilities,
    String contentLoaderClassName,
    String currentTaskId,
    int currentTaskIterationCount,
    List<String> pendingTaskIds,
    List<String> completedTaskIds,
    boolean stopWhenDone,
    boolean teamMember,
    boolean disbanded,
    int awaitingIteration,
    Status status) {

  public enum Status {
    RUNNING,
    IDLE,
    AWAITING_LLM,
    STOPPED
  }

  public static AutonomousAgentState initial(
      String sessionId,
      int maxIterationsPerTask,
      String instructions,
      List<String> toolClassNames,
      List<Capability> capabilities,
      String contentLoaderClassName) {
    return new AutonomousAgentState(
        sessionId,
        maxIterationsPerTask,
        instructions,
        toolClassNames,
        capabilities,
        contentLoaderClassName,
        null,
        0,
        new ArrayList<>(),
        new ArrayList<>(),
        false,
        false,
        false,
        0,
        Status.IDLE);
  }

  public boolean hasCurrentTask() {
    return currentTaskId != null;
  }

  public boolean hasPendingTasks() {
    return !pendingTaskIds.isEmpty();
  }

  public AutonomousAgentState addTask(String taskId) {
    var newPending = new ArrayList<>(pendingTaskIds);
    newPending.add(taskId);
    return new AutonomousAgentState(
        sessionId,
        maxIterationsPerTask,
        instructions,
        toolClassNames,
        capabilities,
        contentLoaderClassName,
        currentTaskId,
        currentTaskIterationCount,
        newPending,
        completedTaskIds,
        stopWhenDone,
        teamMember,
        disbanded,
        awaitingIteration,
        status);
  }

  public AutonomousAgentState withStopWhenDone() {
    return new AutonomousAgentState(
        sessionId,
        maxIterationsPerTask,
        instructions,
        toolClassNames,
        capabilities,
        contentLoaderClassName,
        currentTaskId,
        currentTaskIterationCount,
        pendingTaskIds,
        completedTaskIds,
        true,
        teamMember,
        disbanded,
        awaitingIteration,
        status);
  }

  public AutonomousAgentState withTeamMember() {
    return new AutonomousAgentState(
        sessionId,
        maxIterationsPerTask,
        instructions,
        toolClassNames,
        capabilities,
        contentLoaderClassName,
        currentTaskId,
        currentTaskIterationCount,
        pendingTaskIds,
        completedTaskIds,
        stopWhenDone,
        true,
        false,
        0,
        Status.RUNNING);
  }

  public AutonomousAgentState startNextTask() {
    var nextTaskId = pendingTaskIds.getFirst();
    var remaining = new ArrayList<>(pendingTaskIds.subList(1, pendingTaskIds.size()));
    return new AutonomousAgentState(
        sessionId,
        maxIterationsPerTask,
        instructions,
        toolClassNames,
        capabilities,
        contentLoaderClassName,
        nextTaskId,
        0,
        remaining,
        completedTaskIds,
        stopWhenDone,
        teamMember,
        disbanded,
        0,
        Status.RUNNING);
  }

  public AutonomousAgentState incrementIteration() {
    return new AutonomousAgentState(
        sessionId,
        maxIterationsPerTask,
        instructions,
        toolClassNames,
        capabilities,
        contentLoaderClassName,
        currentTaskId,
        currentTaskIterationCount + 1,
        pendingTaskIds,
        completedTaskIds,
        stopWhenDone,
        teamMember,
        disbanded,
        awaitingIteration,
        status);
  }

  public AutonomousAgentState taskDone() {
    var newCompleted = new ArrayList<>(completedTaskIds);
    if (currentTaskId != null) {
      newCompleted.add(currentTaskId);
    }
    var nextStatus = pendingTaskIds.isEmpty() ? Status.IDLE : Status.RUNNING;
    return new AutonomousAgentState(
        sessionId,
        maxIterationsPerTask,
        instructions,
        toolClassNames,
        capabilities,
        contentLoaderClassName,
        null,
        0,
        pendingTaskIds,
        newCompleted,
        stopWhenDone,
        teamMember,
        disbanded,
        0,
        nextStatus);
  }

  /**
   * Re-queue the current task to the end of the pending list (e.g., when dependencies aren't
   * satisfied yet). Resets iteration count and clears the current task.
   */
  public AutonomousAgentState requeueCurrentTask() {
    var newPending = new ArrayList<>(pendingTaskIds);
    if (currentTaskId != null) {
      newPending.add(currentTaskId);
    }
    return new AutonomousAgentState(
        sessionId,
        maxIterationsPerTask,
        instructions,
        toolClassNames,
        capabilities,
        contentLoaderClassName,
        null,
        0,
        newPending,
        completedTaskIds,
        stopWhenDone,
        teamMember,
        disbanded,
        0,
        Status.RUNNING);
  }

  public AutonomousAgentState withDisbanded() {
    return new AutonomousAgentState(
        sessionId,
        maxIterationsPerTask,
        instructions,
        toolClassNames,
        capabilities,
        contentLoaderClassName,
        currentTaskId,
        currentTaskIterationCount,
        pendingTaskIds,
        completedTaskIds,
        stopWhenDone,
        teamMember,
        true,
        awaitingIteration,
        status);
  }

  public AutonomousAgentState withStopped() {
    return new AutonomousAgentState(
        sessionId,
        maxIterationsPerTask,
        instructions,
        toolClassNames,
        capabilities,
        contentLoaderClassName,
        currentTaskId,
        currentTaskIterationCount,
        pendingTaskIds,
        completedTaskIds,
        stopWhenDone,
        teamMember,
        disbanded,
        0,
        Status.STOPPED);
  }

  public AutonomousAgentState withAwaitingLlm(int iteration) {
    return new AutonomousAgentState(
        sessionId,
        maxIterationsPerTask,
        instructions,
        toolClassNames,
        capabilities,
        contentLoaderClassName,
        currentTaskId,
        currentTaskIterationCount,
        pendingTaskIds,
        completedTaskIds,
        stopWhenDone,
        teamMember,
        disbanded,
        iteration,
        Status.AWAITING_LLM);
  }

  public boolean hasIterationsRemaining() {
    return currentTaskIterationCount < maxIterationsPerTask;
  }
}
