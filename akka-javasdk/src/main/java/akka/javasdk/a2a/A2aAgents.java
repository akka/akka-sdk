/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.a2a;

import akka.javasdk.agent.Agent;
import akka.javasdk.workflow.Workflow;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The agents and task workflows that an {@link AbstractA2aEndpoint} publishes. Each one contributes
 * the skills that its {@link akka.javasdk.annotations.a2a.A2aSkill} annotations describe, or one
 * skill when it has none.
 *
 * <pre>{@code
 * return A2aAgents.create()
 *     .agent(ClaimsAgent.class)
 *     .taskWorkflow(ClaimReviewWorkflow.class);
 * }</pre>
 *
 * <p>Instances are immutable.
 */
public final class A2aAgents {

  // Agents and task workflows in the order they were added, which is the order of their skills
  private final List<Class<?>> published;

  private A2aAgents(List<Class<?>> published) {
    this.published = Collections.unmodifiableList(published);
  }

  /** No agents. Add them with {@link #agent(Class)} and {@link #taskWorkflow(Class)}. */
  public static A2aAgents create() {
    return new A2aAgents(List.of());
  }

  /**
   * Publishes an agent whose skills are answered by one call to the agent. The A2A context id
   * becomes the session id of the agent, so that messages in one context share session memory.
   *
   * @param agentClass the agent, which must be a component of this service
   */
  public A2aAgents agent(Class<? extends Agent> agentClass) {
    return with(agentClass);
  }

  /**
   * Publishes a workflow whose skills are long tasks. Each task starts a workflow with the task id
   * as the workflow id. The messages of the task reach its command handler {@code public
   * Effect<Done> onMessage(A2aMessage message)}, and the workflow reports progress through an
   * injected {@link A2aTaskContext}.
   *
   * @param workflowClass the workflow, which must be a component of this service
   */
  public A2aAgents taskWorkflow(Class<? extends Workflow<?>> workflowClass) {
    return with(workflowClass);
  }

  /** The agents, in the order they were added. */
  @SuppressWarnings("unchecked")
  public List<Class<? extends Agent>> agents() {
    return published.stream()
        .filter(Agent.class::isAssignableFrom)
        .<Class<? extends Agent>>map(c -> (Class<? extends Agent>) c)
        .toList();
  }

  /** The task workflows, in the order they were added. */
  @SuppressWarnings("unchecked")
  public List<Class<? extends Workflow<?>>> taskWorkflows() {
    return published.stream()
        .filter(Workflow.class::isAssignableFrom)
        .<Class<? extends Workflow<?>>>map(c -> (Class<? extends Workflow<?>>) c)
        .toList();
  }

  /** The agents and task workflows, in the order they were added. */
  public List<Class<?>> all() {
    return published;
  }

  private A2aAgents with(Class<?> component) {
    var updated = new ArrayList<>(published);
    updated.add(component);
    return new A2aAgents(updated);
  }
}
