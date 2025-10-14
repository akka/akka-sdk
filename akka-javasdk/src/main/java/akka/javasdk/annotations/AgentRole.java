/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines the role of an agent. This annotation should be placed on agent classes to specify their
 * role.
 *
 * <p>This replaces the {@code role} field in {@link AgentDescription}. If both are present,
 * {@code @AgentRole} takes precedence.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface AgentRole {
  /** The role of the agent. */
  String value();
}
