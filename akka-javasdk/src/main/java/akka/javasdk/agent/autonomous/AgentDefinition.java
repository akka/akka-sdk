/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.autonomous;

import akka.javasdk.agent.Guardrail;
import akka.javasdk.agent.ModelProvider;
import akka.javasdk.agent.RemoteMcpTools;
import akka.javasdk.agent.autonomous.capability.AgentCapability;

/**
 * Defines an autonomous agent's configuration: tools, model provider, guardrails, capabilities, and
 * optional instructions. Built via {@link AutonomousAgent#define()} and returned from {@link
 * AutonomousAgent#definition()}.
 *
 * <p>The {@link akka.javasdk.annotations.Component#description() @Component description} captures
 * the agent's purpose and expected outcome: a short statement of what the agent does, when to use
 * it, and what it produces. The runtime injects the description into the model's system message and
 * uses it when other agents need to choose a delegation or handoff target. The description is
 * mandatory for autonomous agents.
 *
 * <p>{@link #instructions(String) Instructions} are optional supplementary text for tone, persona,
 * domain rules, or procedural guidance to the model, also appended to the system message.
 * Multi-agent orchestration mechanics (when to delegate, when to hand off, who to message) do not
 * belong in instructions — they are derived automatically from the capabilities and from the
 * descriptions of the participating agents.
 *
 * <p>Each fluent method returns a new immutable instance.
 */
public interface AgentDefinition {

  /**
   * Optional internal, LLM-facing instructions appended to the system message. Use this for tone,
   * persona, role, domain rules (for example "Always cite sources" or "Never quote prices in
   * non-USD currencies"), or procedural guidance on how the model should approach a task. The
   * agent's purpose and expected outcome belong in {@link
   * akka.javasdk.annotations.Component#description() @Component description}, not here.
   *
   * <p>Multi-agent orchestration mechanics do not belong here. Coordination details such as when to
   * delegate, when to hand off, or who to message are derived automatically from the capabilities
   * and from the descriptions of the participating agents. If you find yourself writing "delegate
   * to X first, then to Y, then synthesize," the work belongs in capabilities and task definitions
   * instead.
   */
  AgentDefinition instructions(String instructions);

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
