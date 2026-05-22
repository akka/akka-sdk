/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.autonomous.capability;

import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.task.TaskDefinition;
import akka.javasdk.impl.agent.autonomous.capability.TaskAcceptanceImpl;

/**
 * Declares that an agent can accept and process tasks of the specified types. Created via {@link
 * TaskAcceptance#of}.
 *
 * <p>Multiple {@code TaskAcceptance} capabilities can be declared on a single agent to configure
 * different settings (iteration limits, handoff targets) per task group.
 */
public abstract class TaskAcceptance implements AgentCapability {

  /** Create a task acceptance capability for the given task definitions. */
  @SafeVarargs
  public static TaskAcceptance of(TaskDefinition<?> first, TaskDefinition<?>... rest) {
    return TaskAcceptanceImpl.create(first, rest);
  }

  /**
   * Maximum iterations before the agent fails the current task. Default is configured via {@code
   * akka.javasdk.agent.autonomous.max-iterations-per-task} in application.conf.
   */
  public abstract TaskAcceptance maxIterationsPerTask(int max);

  /**
   * Allow this agent to hand off tasks in this group to one of the specified agents. Unlike
   * delegation, handoff transfers ownership — the current agent is done and the target agent takes
   * over.
   */
  @SafeVarargs
  public final TaskAcceptance canHandoffTo(
      Class<? extends AutonomousAgent> first, Class<? extends AutonomousAgent>... rest) {
    return addHandoffTargets(first, rest);
  }

  /** Internal method for adding handoff targets — implemented by the impl class. */
  protected abstract TaskAcceptance addHandoffTargets(
      Class<? extends AutonomousAgent> first, Class<? extends AutonomousAgent>[] rest);
}
