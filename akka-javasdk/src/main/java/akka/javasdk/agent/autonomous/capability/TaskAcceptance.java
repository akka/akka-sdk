/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.autonomous.capability;

import akka.javasdk.agent.autonomous.AutonomousAgent;

/**
 * Builder for task acceptance configuration. Used inside the configuration function passed to
 * {@link akka.javasdk.agent.autonomous.AgentDefinition#canAcceptTasks}.
 *
 * <p>Multiple {@code canAcceptTasks} calls can be made on a single agent to configure different
 * settings (iteration limits, handoff targets) per task group.
 */
public interface TaskAcceptance extends AgentCapability {

  /**
   * Maximum iterations before the agent fails the current task. Default is configured via {@code
   * akka.javasdk.agent.autonomous.max-iterations-per-task} in application.conf.
   */
  TaskAcceptance maxIterationsPerTask(int max);

  /**
   * Allow this agent to hand off tasks in this group to one of the specified agents. Unlike
   * delegation, handoff transfers ownership — the current agent is done and the target agent takes
   * over.
   */
  @SuppressWarnings("unchecked")
  TaskAcceptance canHandoffTo(Class<? extends AutonomousAgent>... agents);
}
