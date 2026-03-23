/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.autonomous;

import akka.javasdk.agent.Guardrail;
import akka.javasdk.agent.MemoryProvider;
import akka.javasdk.agent.ModelProvider;
import akka.javasdk.agent.RemoteMcpTools;
import akka.javasdk.agent.task.TaskDefinition;

/**
 * Defines an autonomous agent's configuration: goal, tools, model provider, guardrails, memory, and
 * capabilities. Built via {@link AutonomousAgent#define()} and returned from {@link
 * AutonomousAgent#definition()}.
 *
 * <p>Each fluent method returns a new immutable instance. Capability entry points ({@code can*}
 * methods) return narrowed builder types with capability-specific modifiers. Calling any general
 * configuration method or another {@code can*} method returns the base {@code AgentDefinition}
 * type, losing the narrowed modifiers.
 */
public interface AgentDefinition {

  /**
   * The agent's high-level purpose. Goal text should describe what the agent achieves, not how it
   * coordinates. The runtime combines the goal with capability-specific context and tool
   * descriptions to build the system message.
   */
  AgentDefinition goal(String goal);

  /** The LLM model provider for this agent. */
  AgentDefinition modelProvider(ModelProvider provider);

  /**
   * Adds one or more tool instances or classes that the agent can use.
   *
   * <p>Each element can be either an object instance or a {@link Class} object. If a {@link Class}
   * is provided, it will be instantiated at runtime using the configured {@link
   * akka.javasdk.DependencyProvider}.
   *
   * <p>Workflows, Event Sourced Entities, Key Value Entities, and Views can also be used as tools.
   * Unlike regular objects, component instances cannot be passed to this method. Instead, you must
   * pass the component {@link Class} object.
   *
   * <p>Each instance or class must have at least one public method annotated with {@link
   * akka.javasdk.annotations.FunctionTool}.
   *
   * @param toolInstancesOrClasses one or more objects or classes exposing tool methods
   */
  AgentDefinition tools(Object... toolInstancesOrClasses);

  /** Remote MCP tool endpoints available to the agent. */
  AgentDefinition mcpTools(RemoteMcpTools... mcpTools);

  /** Guardrails evaluated on requests before they are sent to the LLM. */
  @SuppressWarnings("unchecked")
  AgentDefinition requestGuardrails(Class<? extends Guardrail>... guardrails);

  /** Guardrails evaluated on responses received from the LLM. */
  @SuppressWarnings("unchecked")
  AgentDefinition responseGuardrails(Class<? extends Guardrail>... guardrails);

  /** Session memory configuration for conversation history across iterations. */
  AgentDefinition memory(MemoryProvider memory);

  /**
   * Declare that this agent can accept and process tasks of the specified types. Returns a {@link
   * AutonomousAgent.TaskAcceptanceBuilder} for task-specific modifiers like iteration limits.
   *
   * <p>Multiple calls allow per-task-group settings with different limits.
   */
  @SuppressWarnings("unchecked")
  AutonomousAgent.TaskAcceptanceBuilder canAcceptTasks(TaskDefinition<?>... tasks);

  /**
   * Declare that this agent can hand off tasks to the specified agent. Unlike delegation, handoff
   * transfers ownership — the current agent is done and the target agent takes over.
   *
   * <p>Call multiple times to declare multiple handoff targets.
   */
  AgentDefinition canHandoffTo(Class<? extends AutonomousAgent> agent);

  /**
   * Declare that this agent can delegate subtasks to the specified worker agent. The delegating
   * agent pauses while the worker executes, then resumes with the result.
   *
   * <p>Returns a {@link AutonomousAgent.DelegationBuilder} for delegation-specific modifiers. Call
   * multiple times for different delegation targets.
   */
  AutonomousAgent.DelegationBuilder canDelegateTo(Class<? extends AutonomousAgent> agent);

  /**
   * Declare that this agent can lead a team of autonomous agents. The team lead creates backlogs,
   * adds team members, monitors progress, and disbands the team when work is complete.
   *
   * <p>Returns a {@link AutonomousAgent.TeamBuilder} — at least one {@link
   * AutonomousAgent.TeamBuilder#withMember} call is required before continuing the definition
   * chain.
   */
  AutonomousAgent.TeamBuilder canLeadTeam();
}
