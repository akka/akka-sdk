/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to provide metadata and description for an Agent component.
 * <p>
 * This annotation is essential for multi-agent systems where agents need to be discovered
 * and selected dynamically. The information provided here is used by the {@link akka.javasdk.agent.AgentRegistry}
 * for agent selection and planning.
 * <p>
 * <strong>Agent Selection:</strong>
 * Planning agents can use this metadata to automatically select appropriate agents for specific tasks.
 * The description should clearly explain the agent's capabilities and domain of expertise.
 * <p>
 * <strong>Role-based Organization:</strong>
 * The role field allows grouping agents by function (e.g., "worker", "planner", "coordinator")
 * for easier discovery and organization in complex multi-agent systems.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AgentDescription {
  String name();
  String description();
  String role() default "";
}
