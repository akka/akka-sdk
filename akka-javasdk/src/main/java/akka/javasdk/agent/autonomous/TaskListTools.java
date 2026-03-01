/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.autonomous;

import akka.javasdk.agent.task.TaskEntity;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.client.ComponentClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared task list tools — available when the strategy has task list configured.
 *
 * <p>Provides tools for listing, claiming, and completing tasks from a shared task list. Used by
 * team members to coordinate work.
 */
public class TaskListTools {

  private static final Logger log = LoggerFactory.getLogger(TaskListTools.class);

  private final ComponentClient componentClient;
  private final String taskListId;
  private final String agentType;
  private final String agentId;

  public TaskListTools(
      ComponentClient componentClient, String taskListId, String agentType, String agentId) {
    this.componentClient = componentClient;
    this.taskListId = taskListId;
    this.agentType = agentType;
    this.agentId = agentId;
  }

  @FunctionTool(
      description =
          "List available tasks from the shared task list that can be claimed. Shows task ID,"
              + " description, and status.")
  public String listAvailableTasks() {
    try {
      var state =
          componentClient
              .forEventSourcedEntity(taskListId)
              .method(TaskListEntity::getState)
              .invoke();

      var available =
          state.tasks().stream()
              .filter(t -> t.status() == TaskListState.TaskListItemStatus.AVAILABLE)
              .filter(
                  t ->
                      agentType == null
                          || t.targetAgentTypes() == null
                          || t.targetAgentTypes().isEmpty()
                          || t.targetAgentTypes().contains(agentType))
              .toList();

      if (available.isEmpty()) {
        return "No available tasks in the task list.";
      }

      var sb = new StringBuilder();
      sb.append("Available tasks (").append(available.size()).append("):");
      for (var task : available) {
        sb.append("\n- ")
            .append(task.description())
            .append(" [ID: ")
            .append(task.taskId())
            .append("]");
      }
      return sb.toString();
    } catch (Exception e) {
      log.error("Failed to list tasks from {}", taskListId, e);
      return "Error listing tasks: " + e.getMessage();
    }
  }

  @FunctionTool(
      description =
          "Claim an available task from the shared task list by its task ID. The task will be"
              + " assigned to you.")
  public String claimTask(String taskId) {
    try {
      // Claim in the task list entity
      componentClient
          .forEventSourcedEntity(taskListId)
          .method(TaskListEntity::claimTask)
          .invoke(new TaskListEntity.ClaimTaskRequest(taskId, agentId));

      // Assign and start the underlying task entity
      componentClient.forEventSourcedEntity(taskId).method(TaskEntity::assign).invoke(agentId);
      componentClient.forEventSourcedEntity(taskId).method(TaskEntity::start).invoke();

      log.info("Agent {} claimed task {} from list {}", agentId, taskId, taskListId);
      return "Task " + taskId + " claimed. You can now work on it.";
    } catch (Exception e) {
      log.error("Failed to claim task {} from {}", taskId, taskListId, e);
      return "Error claiming task: " + e.getMessage();
    }
  }

  @FunctionTool(
      description =
          "Complete a claimed task with a result. The task will be marked as completed in the"
              + " shared task list.")
  public String completeClaimedTask(String taskId, String result) {
    try {
      // Complete the underlying task entity
      componentClient.forEventSourcedEntity(taskId).method(TaskEntity::complete).invoke(result);

      // Mark completed in the task list
      componentClient
          .forEventSourcedEntity(taskListId)
          .method(TaskListEntity::completeTask)
          .invoke(taskId);

      log.info("Agent {} completed task {} with result", agentId, taskId);
      return "Task " + taskId + " completed.";
    } catch (Exception e) {
      log.error("Failed to complete task {}", taskId, e);
      return "Error completing task: " + e.getMessage();
    }
  }

  @FunctionTool(description = "Get the status of all tasks in the shared task list.")
  public String getTaskListStatus() {
    try {
      var state =
          componentClient
              .forEventSourcedEntity(taskListId)
              .method(TaskListEntity::getState)
              .invoke();

      var sb = new StringBuilder();
      sb.append("Task list (").append(state.tasks().size()).append(" tasks):");
      for (var task : state.tasks()) {
        sb.append("\n- [").append(task.status()).append("] ").append(task.description());
        if (task.claimedBy() != null) {
          sb.append(" (by ").append(task.claimedBy()).append(")");
        }
        sb.append(" [ID: ").append(task.taskId()).append("]");
      }
      return sb.toString();
    } catch (Exception e) {
      log.error("Failed to get task list status from {}", taskListId, e);
      return "Error getting task list status: " + e.getMessage();
    }
  }
}
