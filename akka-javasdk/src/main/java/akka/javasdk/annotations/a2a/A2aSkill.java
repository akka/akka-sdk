/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.annotations.a2a;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Describes a skill that an agent offers to A2A clients. Each annotation is one entry in the {@code
 * skills} of the Agent Card. Use it on an {@link akka.javasdk.agent.Agent} that an {@link
 * akka.javasdk.a2a.AbstractA2aEndpoint} publishes.
 *
 * <p>An agent without this annotation is published as one skill. Its id is the component id, and
 * its name and description are those of the {@link akka.javasdk.annotations.Component} annotation.
 *
 * <p>Skill ids are unique within a service. A client selects a skill with the {@code skillId} field
 * in the metadata of its message.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Repeatable(A2aSkills.class)
public @interface A2aSkill {

  /** The id of the skill. Letters, digits, {@code _}, {@code -} and {@code .} are allowed. */
  String id();

  /** The name of the skill. Defaults to the id. */
  String name() default "";

  /** What the skill does. Defaults to the description of the agent. */
  String description() default "";

  /** Keywords that describe the skill. */
  String[] tags() default {};

  /** Example requests for the skill. */
  String[] examples() default {};

  /** The media types the skill accepts. Defaults to the input modes of the Agent Card. */
  String[] inputModes() default {};

  /** The media types the skill returns. Defaults to the output modes of the Agent Card. */
  String[] outputModes() default {};
}
