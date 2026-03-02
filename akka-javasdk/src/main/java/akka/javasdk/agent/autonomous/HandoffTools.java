/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.autonomous;

import akka.javasdk.agent.task.TaskEntity;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.client.ComponentClient;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handoff tools — available when the strategy has handoff configured.
 *
 * <p>Unlike delegation, handoff does not create a child task or poll for results. It reassigns the
 * current task to a new agent and returns immediately. The current agent should stop working on the
 * task after a handoff.
 */
public class HandoffTools {

  private static final Logger log = LoggerFactory.getLogger(HandoffTools.class);

  private final ComponentClient componentClient;
  private final List<HandoffCapability> configs;
  private final String taskId;

  public HandoffTools(
      ComponentClient componentClient, List<HandoffCapability> configs, String taskId) {
    this.componentClient = componentClient;
    this.configs = configs;
    this.taskId = taskId;
  }

  @FunctionTool(
      description =
          "Hand off the current task to a specialist agent. The current agent will stop working"
              + " on this task. Provide the agent ID and context about what has been done and what"
              + " the next agent should focus on.")
  public String handoffTask(String agent, String context) {
    var config =
        configs.stream().filter(c -> c.agentComponentId().equals(agent)).findFirst().orElse(null);

    if (config == null) {
      return "Error: Unknown agent '"
          + agent
          + "'. Available agents: "
          + configs.stream().map(HandoffCapability::agentComponentId).toList();
    }

    var workerInstanceId = config.agentComponentId() + "-" + UUID.randomUUID();

    // Start the new agent workflow first (fail fast if class not found)
    try {
      var agentClass = Class.forName(config.agentClassName()).asSubclass(AutonomousAgent.class);
      var agentClient = componentClient.forAutonomousAgent(workerInstanceId, agentClass);
      agentClient.start();

      // Reassign the task (updates assignee, appends context)
      componentClient
          .forEventSourcedEntity(taskId)
          .method(TaskEntity::handoff)
          .invoke(new TaskEntity.HandoffRequest(workerInstanceId, context));

      // Assign the already-in-progress task to the new agent
      agentClient.assignHandedOffTask(taskId);
    } catch (Exception e) {
      log.error("Failed to hand off task to agent: {}", config.agentClassName(), e);
      return "Error: Failed to hand off task: " + e.getMessage();
    }

    log.info(
        "Task {} handed off to {} (instance {})",
        taskId,
        config.agentComponentId(),
        workerInstanceId);

    return "Task handed off to "
        + config.agentComponentId()
        + ". You should stop working on this task now.";
  }
}
