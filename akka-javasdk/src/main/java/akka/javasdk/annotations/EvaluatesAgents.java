/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Container annotation for repeated {@link EvaluatesAgent} annotations.
 *
 * <p>Not used directly — apply {@link EvaluatesAgent} multiple times instead.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface EvaluatesAgents {

  /**
   * The contained {@link EvaluatesAgent} annotations.
   *
   * @return the agent bindings
   */
  EvaluatesAgent[] value();
}
