/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.autonomous;

import akka.javasdk.agent.Agent;

/**
 * An autonomous AI agent component that operates independently to complete tasks.
 *
 * <p>Unlike a request-based {@link Agent}, an autonomous agent runs a durable execution loop: call
 * LLM, execute tools, check task status, repeat — until the task is complete or the iteration limit
 * is reached.
 *
 * <p>Subclasses must implement {@link #strategy()} to configure the agent's behavior: goal, tools,
 * model provider, accepted task definitions, and iteration limits.
 *
 * <p><strong>Component Identification:</strong> The agent must be annotated with {@link
 * akka.javasdk.annotations.Component} to provide a unique identifier.
 *
 * @see Strategy
 * @see AutonomousStrategy
 */
public abstract class AutonomousAgent {

  /**
   * Define the strategy for this autonomous agent. The strategy configures the agent's goal, tools,
   * model provider, accepted task definitions, and iteration limits.
   *
   * @return the strategy for autonomous execution
   */
  public abstract Strategy strategy();
}
