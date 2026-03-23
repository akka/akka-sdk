/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.autonomous;

import akka.javasdk.agent.task.TaskDefinition;
import akka.javasdk.impl.agent.autonomous.AgentSetupImpl;

/**
 * Per-instance configuration applied on top of an agent's static {@link AgentDefinition}. Use this
 * to dynamically configure an agent instance at runtime — the goal overrides the static goal, and
 * capabilities extend the static capabilities.
 *
 * <p>Each fluent method returns a new immutable instance. Capability entry points ({@code can*}
 * methods) return narrowed builder types with capability-specific modifiers.
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

  /**
   * Declare that this agent can accept and process tasks of the specified types. Returns a {@link
   * TaskAcceptanceBuilder} for task-specific modifiers like iteration limits.
   */
  @SuppressWarnings("unchecked")
  TaskAcceptanceBuilder canAcceptTasks(TaskDefinition<?>... tasks);

  /**
   * Declare that this agent can hand off tasks to the specified agent. Unlike delegation, handoff
   * transfers ownership — the current agent is done and the target agent takes over.
   */
  AgentSetup canHandoffTo(Class<? extends AutonomousAgent> agent);

  /**
   * Declare that this agent can delegate subtasks to the specified worker agent. Returns a {@link
   * DelegationBuilder} for delegation-specific modifiers.
   */
  DelegationBuilder canDelegateTo(Class<? extends AutonomousAgent> agent);

  /**
   * Declare that this agent can lead a team of autonomous agents. Returns a {@link TeamBuilder} —
   * at least one {@link TeamBuilder#withMember} call is required before continuing the setup chain.
   */
  TeamBuilder canLeadTeam();

  // -- Inner builder interfaces for AgentSetup capability chains --

  /**
   * Builder returned after {@link AgentSetup#canAcceptTasks}. Provides task-acceptance-specific
   * modifiers.
   */
  interface TaskAcceptanceBuilder extends AgentSetup {

    /**
     * Maximum iterations before the agent fails the current task. Default is configured via {@code
     * akka.javasdk.agent.autonomous.max-iterations-per-task} in application.conf.
     */
    TaskAcceptanceBuilder maxIterationsPerTask(int max);
  }

  /**
   * Builder returned after {@link AgentSetup#canDelegateTo}. Provides delegation-specific
   * modifiers.
   */
  interface DelegationBuilder extends AgentSetup {

    /**
     * Maximum number of worker agents that can execute delegated subtasks concurrently. Default is
     * configured via {@code akka.javasdk.agent.autonomous.delegation.max-parallel-workers} in
     * application.conf.
     */
    DelegationBuilder maxParallelWorkers(int max);
  }

  /**
   * Builder returned after {@link AgentSetup#canLeadTeam}. Does <em>not</em> extend {@link
   * AgentSetup} — at least one {@link #withMember} call is required.
   */
  interface TeamBuilder {

    /** Add a team member type. At least one member is required. */
    TeamMemberBuilder withMember(Class<? extends AutonomousAgent> agentClass);
  }

  /**
   * Builder returned after {@link TeamBuilder#withMember}. Provides member-specific modifiers and
   * allows adding more members. Extends {@link AgentSetup} so the chain can continue.
   */
  interface TeamMemberBuilder extends AgentSetup {

    /** Maximum number of instances of this member type that can be added to the team. */
    TeamMemberBuilder maxInstances(int max);

    /** Add another team member type. */
    TeamMemberBuilder withMember(Class<? extends AutonomousAgent> agentClass);
  }
}
