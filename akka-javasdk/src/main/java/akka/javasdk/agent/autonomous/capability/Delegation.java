/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.autonomous.capability;

import akka.javasdk.agent.AgentDelegationWorker;
import akka.javasdk.impl.agent.autonomous.capability.DelegationImpl;

/**
 * Declares that an agent can delegate subtasks to other agents. The delegating agent pauses while
 * workers execute, then resumes with their results.
 *
 * <p>Created via {@link Delegation#to}, accepting both autonomous ({@link
 * akka.javasdk.agent.autonomous.AutonomousAgent}) and request-based ({@link
 * akka.javasdk.agent.Agent}) agent targets.
 */
public interface Delegation extends AgentCapability {

  /**
   * Create a delegation capability for the given agent targets. Accepts both autonomous agents and
   * request-based agents.
   */
  @SafeVarargs
  static Delegation to(
      Class<? extends AgentDelegationWorker> first,
      Class<? extends AgentDelegationWorker>... rest) {
    return DelegationImpl.create(first, rest);
  }

  /**
   * Maximum number of worker agents that can execute delegated subtasks concurrently. Default is
   * configured via {@code akka.javasdk.agent.autonomous.delegation.max-parallel-workers} in
   * application.conf.
   */
  Delegation maxParallelWorkers(int max);
}
