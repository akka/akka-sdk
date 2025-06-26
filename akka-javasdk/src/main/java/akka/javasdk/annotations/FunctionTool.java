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
 * Annotation to expose methods as tools for use by an Agent.
 * Methods annotated with {@code FunctionTool} can be discovered and invoked by the Agent as callable tools.
 * <p>
 * It's recommended to annotate the method parameters with {@link Description} and provide a clear description.
 * Parameters of type {@code Optional} are considered as non-required.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface FunctionTool {

  String name() default "";
  String description();
}
