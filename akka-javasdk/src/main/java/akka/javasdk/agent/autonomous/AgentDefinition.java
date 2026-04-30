/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.autonomous;

import akka.javasdk.agent.Guardrail;
import akka.javasdk.agent.ModelProvider;
import akka.javasdk.agent.RemoteMcpTools;
import akka.javasdk.agent.autonomous.capability.AgentCapability;

/**
 * Defines an autonomous agent's configuration: purpose, guidance, tools, model provider,
 * guardrails, and capabilities. Built via {@link AutonomousAgent#define()} and returned from {@link
 * AutonomousAgent#definition()}.
 *
 * <p>Each fluent method returns a new immutable instance.
 */
public interface AgentDefinition {

  /**
   * The agent's high-level purpose. Describes what the agent is for — not how it operates. The
   * runtime combines the purpose with optional guidance, capability-specific context, and tool
   * descriptions to build the system message.
   */
  AgentDefinition purpose(String purpose);

  /**
   * Optional guidance on how the agent should operate — style, conventions, behavioral preferences.
   * Distinct from {@link #purpose}, which describes what the agent is for, and from task-level
   * instructions, which apply only to a specific task.
   */
  AgentDefinition guidance(String guidance);

  /** Add a capability to this agent: task acceptance, delegation, etc. */
  AgentDefinition capability(AgentCapability capability);

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
}
