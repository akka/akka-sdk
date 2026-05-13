/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.annotations;

import java.lang.annotation.*;

/**
 * Assign metadata to a component (required for all component types aside from Endpoints).
 *
 * <p>The id should be unique among the different components.
 *
 * <p>In the case of Entities, Workflows and Views, the component id should be stable as a different
 * identifier means a different representation in storage. Changing this identifier will create a
 * new class of component and all previous instances using the old identifier won't be accessible
 * anymore.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Component {
  /** The unique identifier for this component (mandatory). */
  String id();

  /** A human-readable name for this component (optional). */
  String name() default "";

  /**
   * A description of what this component does.
   *
   * <p>Optional in general, but <strong>mandatory and non-empty</strong> for classes extending
   * {@code AutonomousAgent}. For agents (both request-based and autonomous), the description
   * captures the agent's purpose and expected outcome: it is injected into the model's system
   * message and is used by other agents to decide whether to delegate or hand off to this agent.
   * Write it as a short statement of what the agent does, when to use it, and what it produces,
   * rather than as a procedure.
   */
  String description() default "";
}
