/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
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

  /** A description of what this component does (optional). */
  String description() default "";
}
