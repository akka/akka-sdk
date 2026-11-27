/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.annotations.a2a;

import java.lang.annotation.*;

/**
 * A skill listed in the Agent Card of an {@link A2aEndpoint}.
 *
 * <p>A skill only describes what the agent can do to callers. A2A has no skill selector, so the
 * endpoint receives every message and routes it.
 */
@Target({})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface A2aSkill {
  /**
   * @return Identifier of the skill, unique in the endpoint
   */
  String id();

  /**
   * @return Human readable name of the skill
   */
  String name();

  /**
   * @return Description of what the skill does
   */
  String description();

  /**
   * @return Keywords that describe the skill
   */
  String[] tags() default {};

  /**
   * @return Example prompts for the skill
   */
  String[] examples() default {};
}
