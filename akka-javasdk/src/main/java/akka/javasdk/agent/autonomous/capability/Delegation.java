/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.autonomous.capability;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.impl.agent.autonomous.capability.DelegationImpl;

/**
 * Declares that an agent can delegate subtasks to other agents. The delegating agent pauses while
 * workers execute, then resumes with their results.
 *
 * <p>Created via {@link Delegation#to} for autonomous agents or {@link Delegation#toRequestBased}
 * for request-based agents.
 */
public interface Delegation extends AgentCapability {

  /** Create a delegation capability for the given autonomous agent targets. */
  @SuppressWarnings("unchecked")
  static Delegation to(Class<? extends AutonomousAgent>... agents) {
    return DelegationImpl.create(agents);
  }

  /** Create a delegation capability for the given request-based agent targets. */
  @SuppressWarnings("unchecked")
  static Delegation toRequestBased(Class<? extends Agent>... agents) {
    return DelegationImpl.createRequestBased(agents);
  }

  /**
   * Maximum number of worker agents that can execute delegated subtasks concurrently. Default is
   * configured via {@code akka.javasdk.agent.autonomous.delegation.max-parallel-workers} in
   * application.conf.
   */
  Delegation maxParallelWorkers(int max);
}
