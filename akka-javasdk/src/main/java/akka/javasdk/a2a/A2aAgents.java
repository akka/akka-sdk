/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.a2a;

import akka.javasdk.agent.Agent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The agents that an {@link AbstractA2aEndpoint} publishes. Each agent contributes the skills that
 * its {@link akka.javasdk.annotations.a2a.A2aSkill} annotations describe, or one skill when it has
 * none.
 *
 * <pre>{@code
 * return A2aAgents.create().agent(ClaimsAgent.class).agent(PolicyAgent.class);
 * }</pre>
 *
 * <p>Instances are immutable.
 */
public final class A2aAgents {

  private final List<Class<? extends Agent>> agents;

  private A2aAgents(List<Class<? extends Agent>> agents) {
    this.agents = Collections.unmodifiableList(agents);
  }

  /** No agents. Add them with {@link #agent(Class)}. */
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
    var updated = new ArrayList<>(agents);
    updated.add(agentClass);
    return new A2aAgents(updated);
  }

  /** The agents, in the order they were added. */
  public List<Class<? extends Agent>> agents() {
    return agents;
  }
}
