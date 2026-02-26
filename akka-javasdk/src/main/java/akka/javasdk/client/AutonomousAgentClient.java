/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.client;

import akka.Done;
import akka.annotation.DoNotInherit;
import akka.javasdk.agent.autonomous.Capability;
import java.util.List;

/** Not for user extension, implementation provided by the SDK. */
@DoNotInherit
public interface AutonomousAgentClient {

  /**
   * Create a task and run it on this agent. The agent auto-starts, processes the task, and stops
   * when done. Returns the task ID for polling results.
   *
   * @param description The task description.
   * @param resultType The expected result type.
   * @return The generated task ID.
   */
  String runSingleTask(String description, Class<?> resultType);

  /** Start the autonomous agent. It begins idle, waiting for tasks to be assigned. */
  Done start();

  /**
   * Assign a task to this autonomous agent. If the agent is idle it will start processing
   * immediately. If the agent is already processing a task, this one will be queued.
   *
   * @param taskId The ID of the task to assign.
   */
  Done assignTask(String taskId);

  /**
   * Assign a single task and stop the agent when it completes. Use this for one-shot agents that
   * process a single task and then clean up automatically.
   *
   * @param taskId The ID of the task to assign.
   */
  Done assignSingleTask(String taskId);

  /**
   * Assign a task that has been handed off from another agent. The task is already IN_PROGRESS with
   * this agent as the assignee. The agent will process it and stop when done.
   *
   * @param taskId The ID of the handed-off task.
   */
  Done assignHandedOffTask(String taskId);

  /**
   * Start this agent as a team member with additional team-specific capabilities (task list,
   * messaging) injected by the team lead.
   *
   * @param teamCapabilities Capabilities to merge with the agent's base strategy.
   */
  Done startAsTeamMember(List<Capability> teamCapabilities);

  /** Stop the autonomous agent, ending its workflow. */
  Done stop();
}
