/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.swarm;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.Optional;

/**
 * Defines a handoff target for a class-based swarm. A handoff is a delegation from the
 * orchestrator LLM to another agent or another swarm.
 *
 * <p>Both agents and swarms can be referenced by component ID (string) or by class.
 * When referenced by class, the component ID is resolved from the {@code @Component}
 * annotation at runtime.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
@JsonSubTypes({
    @JsonSubTypes.Type(value = Handoff.AgentHandoff.class, name = "AgentHandoff"),
    @JsonSubTypes.Type(value = Handoff.SwarmHandoff.class, name = "SwarmHandoff")
})
public sealed interface Handoff {

  /** Optional description used in the LLM tool description. */
  Optional<String> description();

  /**
   * Handoff to an agent component.
   *
   * @param componentId the agent's component ID (or class-derived ID)
   * @param description optional description for the LLM tool
   */
  record AgentHandoff(String componentId, Optional<String> description) implements Handoff {
    public AgentHandoff(String componentId) {
      this(componentId, Optional.empty());
    }
  }

  /**
   * Handoff to another swarm component.
   *
   * @param componentId the swarm's component ID (or class-derived ID)
   * @param description optional description for the LLM tool
   */
  record SwarmHandoff(String componentId, Optional<String> description) implements Handoff {
    public SwarmHandoff(String componentId) {
      this(componentId, Optional.empty());
    }
  }

  // ========== Agent handoffs ==========

  /** Create a handoff to an agent by component ID. */
  static Handoff toAgent(String agentId) {
    return new AgentHandoff(agentId);
  }

  /**
   * Create a handoff to an agent by class.
   * The component ID is resolved from the {@code @Component} annotation.
   */
  static Handoff toAgent(Class<?> agentClass) {
    return new AgentHandoff(resolveComponentId(agentClass));
  }

  // ========== Swarm handoffs ==========

  /** Create a handoff to another swarm by component ID. */
  static Handoff toSwarm(String swarmId) {
    return new SwarmHandoff(swarmId);
  }

  /**
   * Create a handoff to another swarm by class.
   * The component ID is resolved from the {@code @Component} annotation.
   */
  static Handoff toSwarm(Class<?> swarmClass) {
    return new SwarmHandoff(resolveComponentId(swarmClass));
  }

  /** Return a copy with the given description, used in the LLM tool description. */
  default Handoff withDescription(String description) {
    return switch (this) {
      case AgentHandoff a -> new AgentHandoff(a.componentId(), Optional.of(description));
      case SwarmHandoff s -> new SwarmHandoff(s.componentId(), Optional.of(description));
    };
  }

  /** Resolve component ID from the {@code @Component} annotation on the class. */
  private static String resolveComponentId(Class<?> componentClass) {
    var annotation = componentClass.getAnnotation(akka.javasdk.annotations.Component.class);
    if (annotation == null) {
      throw new IllegalArgumentException(
          componentClass.getName() + " is not annotated with @Component");
    }
    return annotation.id();
  }
}
