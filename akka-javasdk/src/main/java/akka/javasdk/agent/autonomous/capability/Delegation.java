/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.autonomous.capability;

/**
 * Builder for delegation configuration. Used inside the configuration function passed to {@link
 * akka.javasdk.agent.autonomous.AgentDefinition#canDelegateTo}.
 */
public interface Delegation extends AgentCapability {

  /**
   * Maximum number of worker agents that can execute delegated subtasks concurrently. Default is
   * configured via {@code akka.javasdk.agent.autonomous.delegation.max-parallel-workers} in
   * application.conf.
   */
  Delegation maxParallelWorkers(int max);
}
