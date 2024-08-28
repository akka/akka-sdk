/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.annotations;

import java.lang.annotation.*;

/**
 * Assign a type identifier to a component (Entities, Workflows, Views or Actions).
 *
 * The identifier should be unique among the different components.
 *
 * In the case of Entities, Workflows and Views, the ComponentId should be stable as a different identifier means a
 * different representation in storage. Changing this identifier will create a new class of component and all previous
 * instances using the old identifier won't be accessible anymore.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ComponentId {
  String value();
}
