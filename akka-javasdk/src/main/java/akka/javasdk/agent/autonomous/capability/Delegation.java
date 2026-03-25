/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.autonomous.capability;

import akka.javasdk.agent.autonomous.AutonomousAgent;

/**
 * Declares that an agent can delegate subtasks to other autonomous agents. The delegating agent
 * pauses while workers execute, then resumes with their results.
 *
 * <p>Created via {@link Delegation#to}.
 */
public interface Delegation extends AgentCapability {

  /** Create a delegation capability for the given target agents. */
  @SuppressWarnings("unchecked")
  static Delegation to(Class<? extends AutonomousAgent>... agents) {
    return akka.javasdk.impl.agent.autonomous.capability.DelegationImpl.create(agents);
  }

  /**
   * Maximum number of worker agents that can execute delegated subtasks concurrently. Default is
   * configured via {@code akka.javasdk.agent.autonomous.delegation.max-parallel-workers} in
   * application.conf.
   */
  Delegation maxParallelWorkers(int max);
}
