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
 * An autonomous strategy backed by an LLM decision loop with durable execution. The agent iterates:
 * call LLM, execute tools, check task status, repeat — until the task is complete or the iteration
 * limit is reached.
 */
public sealed interface AutonomousStrategy extends Strategy permits DefaultAutonomousStrategy {

  /** Default maximum iterations before the agent fails the current task. */
  int DEFAULT_MAX_ITERATIONS = 10;

  /**
   * The agent's high-level purpose. Goal text should describe what the agent achieves, not how it
   * coordinates. The runtime combines the goal with capability-specific context and tool
   * descriptions to build the system message.
   */
  AutonomousStrategy goal(String goal);

  /** Declare which task definitions this agent accepts. */
  AutonomousStrategy accepts(TaskDefinition<?>... tasks);

  /** The LLM model provider for this agent. */
  AutonomousStrategy modelProvider(ModelProvider provider);

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
  AutonomousStrategy tools(Object... toolInstancesOrClasses);

  /** Remote MCP tool endpoints available to the agent. */
  AutonomousStrategy mcpTools(RemoteMcpTools... mcpTools);

  /** Guardrails evaluated on requests before they are sent to the LLM. */
  AutonomousStrategy requestGuardrails(Class<? extends Guardrail>... guardrails);

  /** Guardrails evaluated on responses received from the LLM. */
  AutonomousStrategy responseGuardrails(Class<? extends Guardrail>... guardrails);

  /** Session memory configuration for conversation history across iterations. */
  AutonomousStrategy memory(MemoryProvider memory);

  /**
   * Allow this agent to delegate subtasks to the specified worker agents. The coordinator pauses
   * while workers execute, then resumes with their results. Multiple calls accumulate targets.
   */
  AutonomousStrategy canDelegateTo(Class<? extends AutonomousAgent>... agents);

  /**
   * Allow this agent to hand off its current task to one of the specified agents. Unlike
   * delegation, handoff transfers ownership — the current agent is done and the target agent takes
   * over. Multiple calls accumulate targets.
   */
  AutonomousStrategy canHandoffTo(Class<? extends AutonomousAgent>... agents);

  /**
   * Maximum iterations before the agent fails the current task. Default {@link
   * #DEFAULT_MAX_ITERATIONS}.
   */
  AutonomousStrategy maxIterations(int max);
}
