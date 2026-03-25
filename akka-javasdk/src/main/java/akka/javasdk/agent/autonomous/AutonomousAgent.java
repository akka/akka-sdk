/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.autonomous;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.autonomous.capability.AgentCapability;
import akka.javasdk.agent.autonomous.capability.Delegation;
import akka.javasdk.agent.autonomous.capability.TaskAcceptance;
import akka.javasdk.agent.autonomous.capability.TeamLeadership;
import akka.javasdk.agent.task.TaskDefinition;
import akka.javasdk.impl.agent.autonomous.AgentDefinitionImpl;

/**
 * An autonomous AI agent component that operates independently to complete tasks.
 *
 * <p>Unlike a request-based {@link Agent}, an autonomous agent runs a durable execution loop: call
 * LLM, execute tools, check task status, repeat — until the task is complete or the iteration limit
 * is reached.
 *
 * <p>Subclasses must implement {@link #definition()} to configure the agent's behavior: goal,
 * tools, model provider, and capabilities.
 *
 * <p><strong>Component Identification:</strong> The agent must be annotated with {@link
 * akka.javasdk.annotations.Component} to provide a unique identifier.
 *
 * @see AgentDefinition
 * @see AgentCapability
 */
public abstract class AutonomousAgent {

  /**
   * Define this autonomous agent. The definition configures the agent's goal, tools, model
   * provider, guardrails, memory, and capabilities.
   *
   * @return the agent definition
   */
  public abstract AgentDefinition definition();

  /** Start building an agent definition. */
  protected final AgentDefinition define() {
    return AgentDefinitionImpl.empty();
  }

  /**
   * Declare that this agent can accept and process tasks of the specified types. Returns a {@link
   * TaskAcceptance} capability that can be further configured with iteration limits and handoff
   * targets.
   *
   * <p>Multiple calls with different task definitions allow per-task-group settings.
   */
  @SafeVarargs
  protected final TaskAcceptance canAcceptTasks(TaskDefinition<?>... tasks) {
    return TaskAcceptance.of(tasks);
  }

  /**
   * Declare that this agent can delegate subtasks to the specified worker agents. The delegating
   * agent pauses while workers execute, then resumes with their results.
   */
  @SafeVarargs
  protected final Delegation canDelegateTo(Class<? extends AutonomousAgent>... agents) {
    return Delegation.to(agents);
  }

  /**
   * Declare that this agent can lead a team of autonomous agents. The team lead creates backlogs,
   * adds team members, monitors progress, and disbands the team when work is complete.
   */
  protected final TeamLeadership canLeadTeam(TeamLeadership.MemberType... memberTypes) {
    return TeamLeadership.of(memberTypes);
  }

  /**
   * Define a team member type. Used with {@link #canLeadTeam} to specify which agent types can be
   * added to the team.
   */
  protected final TeamLeadership.MemberType teamMember(
      Class<? extends AutonomousAgent> agentClass) {
    return TeamLeadership.MemberType.of(agentClass);
  }
}
