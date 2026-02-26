/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.autonomous;

import akka.javasdk.agent.task.TaskEntity;
import akka.javasdk.agent.task.TaskState;
import akka.javasdk.agent.task.TaskStatus;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.client.ComponentClient;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Delegation tools — available when the strategy has delegation configured.
 *
 * <p>Delegations run synchronously within the agent's tool call loop. The delegate tool creates a
 * subtask, starts a worker agent, and polls until the worker completes before returning.
 */
public class DelegationTools {

  private static final Logger log = LoggerFactory.getLogger(DelegationTools.class);

  private static final long POLL_INTERVAL_MS = 1000;
  private static final long POLL_TIMEOUT_MS = 4 * 60 * 1000; // 4 minutes

  private final ComponentClient componentClient;
  private final List<DelegationCapability> configs;

  public DelegationTools(ComponentClient componentClient, List<DelegationCapability> configs) {
    this.componentClient = componentClient;
    this.configs = configs;
  }

  @FunctionTool(
      description =
          "Delegate a subtask to a specialist agent. Specify the agent ID and a description"
              + " of the subtask. The agent will work on the subtask and return the result.")
  public String delegate(String agent, String taskDescription) {
    var config =
        configs.stream().filter(c -> c.agentComponentId().equals(agent)).findFirst().orElse(null);

    if (config == null) {
      return "Error: Unknown agent '"
          + agent
          + "'. Available agents: "
          + configs.stream().map(DelegationCapability::agentComponentId).toList();
    }

    var subtaskId = UUID.randomUUID().toString();
    var workerInstanceId = config.agentComponentId() + "-" + subtaskId;

    // Create subtask
    componentClient
        .forEventSourcedEntity(subtaskId)
        .method(TaskEntity::create)
        .invoke(new TaskEntity.CreateRequest(taskDescription, null));

    // Start worker agent and assign as single task (auto-stops when done)
    try {
      var agentClass = Class.forName(config.agentClassName()).asSubclass(AutonomousAgent.class);
      var agentClient = componentClient.forAutonomousAgent(workerInstanceId, agentClass);
      agentClient.start();
      agentClient.assignSingleTask(subtaskId);
    } catch (Exception e) {
      log.error("Failed to start worker agent: {}", config.agentClassName(), e);
      return "Error: Failed to start worker agent: " + e.getMessage();
    }

    // Poll until the subtask reaches a terminal state
    TaskState subtaskState = pollForCompletion(subtaskId, config.agentComponentId());

    log.info(
        "Delegation to {} complete: subtask {} status {}",
        config.agentComponentId(),
        subtaskId,
        subtaskState.status());

    if (subtaskState.result() != null && !subtaskState.result().isEmpty()) {
      return "Result from " + config.agentComponentId() + ": " + subtaskState.result();
    } else {
      return "Agent "
          + config.agentComponentId()
          + " finished with status: "
          + subtaskState.status();
    }
  }

  // Prototype only — the runtime will handle delegation completion natively without polling.
  private TaskState pollForCompletion(String subtaskId, String agentId) {
    long deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS;

    while (System.currentTimeMillis() < deadline) {
      try {
        Thread.sleep(POLL_INTERVAL_MS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        log.warn("Polling interrupted for subtask {}", subtaskId);
        break;
      }

      var state =
          componentClient.forEventSourcedEntity(subtaskId).method(TaskEntity::getState).invoke();

      if (state.status() == TaskStatus.COMPLETED || state.status() == TaskStatus.FAILED) {
        return state;
      }

      log.debug("Subtask {} for agent {} still {}", subtaskId, agentId, state.status());
    }

    log.warn("Subtask {} for agent {} timed out after polling", subtaskId, agentId);
    // Return whatever state we have
    return componentClient.forEventSourcedEntity(subtaskId).method(TaskEntity::getState).invoke();
  }
}
