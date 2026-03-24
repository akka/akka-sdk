/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.autonomous;

import akka.javasdk.agent.autonomous.capability.Delegation;
import akka.javasdk.agent.autonomous.capability.TaskAcceptance;
import akka.javasdk.agent.autonomous.capability.TeamLeadership;
import akka.javasdk.agent.task.TaskDefinition;
import akka.javasdk.impl.agent.autonomous.AgentSetupImpl;
import java.util.function.UnaryOperator;

/**
 * Per-instance configuration applied on top of an agent's static {@link AgentDefinition}. Use this
 * to dynamically configure an agent instance at runtime — the goal overrides the static goal, and
 * capabilities extend the static capabilities.
 *
 * <p>Each fluent method returns a new immutable instance.
 *
 * @see AutonomousAgent#definition()
 */
public interface AgentSetup {

  /** Create an empty agent setup. */
  static AgentSetup create() {
    return AgentSetupImpl.empty();
  }

  /**
   * Override the agent's goal for this instance. If not set, the static goal from {@link
   * AgentDefinition} is used.
   */
  AgentSetup goal(String goal);

  /** Declare that this agent instance can accept and process a task of the specified type. */
  AgentSetup canAcceptTask(TaskDefinition<?> task);

  /** Declare that this agent instance can accept and process a task, with custom settings. */
  AgentSetup canAcceptTask(TaskDefinition<?> task, UnaryOperator<TaskAcceptance> config);

  /** Declare that this agent instance can delegate subtasks to the specified worker agent. */
  AgentSetup canDelegateTo(Class<? extends AutonomousAgent> agent);

  /** Declare that this agent instance can delegate subtasks, with custom settings. */
  AgentSetup canDelegateTo(
      Class<? extends AutonomousAgent> agent, UnaryOperator<Delegation> config);

  /** Declare that this agent instance can lead a team of autonomous agents. */
  AgentSetup canLeadTeam(UnaryOperator<TeamLeadership> config);
}
