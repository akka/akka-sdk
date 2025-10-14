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
 * @deprecated Use {@link Component} for specifying the agent's name and description.
 *     <p>To assign a role to an agent, use the {@link AgentRole} annotation.
 *     <p>This annotation is retained for backward compatibility but should not be used in new code.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Deprecated
public @interface AgentDescription {
  String name();

  String description();

  String role() default "";
}
