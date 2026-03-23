/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.autonomous;

import akka.javasdk.agent.Agent;
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

  // -- Inner builder interfaces for AgentDefinition capability chains --

  /**
   * Builder returned after {@link AgentDefinition#canAcceptTasks}. Provides
   * task-acceptance-specific modifiers that apply to the most recently declared task acceptance
   * capability.
   *
   * <p>Calling any {@link AgentDefinition} method (e.g., {@code goal()}, another {@code can*})
   * returns the base type, losing these modifier methods.
   */
  public interface TaskAcceptanceBuilder extends AgentDefinition {

    /**
     * Maximum iterations before the agent fails the current task. Default is configured via {@code
     * akka.javasdk.agent.autonomous.max-iterations-per-task} in application.conf.
     */
    TaskAcceptanceBuilder maxIterationsPerTask(int max);
  }

  /**
   * Builder returned after {@link AgentDefinition#canDelegateTo}. Provides delegation-specific
   * modifiers that apply to the most recently declared delegation capability.
   *
   * <p>Calling any {@link AgentDefinition} method (e.g., {@code goal()}, another {@code can*})
   * returns the base type, losing these modifier methods.
   */
  public interface DelegationBuilder extends AgentDefinition {

    /**
     * Maximum number of worker agents that can execute delegated subtasks concurrently. Default is
     * configured via {@code akka.javasdk.agent.autonomous.delegation.max-parallel-workers} in
     * application.conf.
     */
    DelegationBuilder maxParallelWorkers(int max);
  }

  /**
   * Builder returned after {@link AgentDefinition#canLeadTeam}. Does <em>not</em> extend {@link
   * AgentDefinition} — at least one {@link #withMember} call is required before continuing the
   * definition chain.
   */
  public interface TeamBuilder {

    /** Add a team member type. At least one member is required. */
    TeamMemberBuilder withMember(Class<? extends AutonomousAgent> agentClass);
  }

  /**
   * Builder returned after {@link TeamBuilder#withMember}. Provides member-specific modifiers and
   * allows adding more members. Extends {@link AgentDefinition} so the chain can continue with
   * general configuration or additional capabilities.
   */
  public interface TeamMemberBuilder extends AgentDefinition {

    /** Maximum number of instances of this member type that can be added to the team. */
    TeamMemberBuilder maxInstances(int max);

    /** Add another team member type. */
    TeamMemberBuilder withMember(Class<? extends AutonomousAgent> agentClass);
  }
}
