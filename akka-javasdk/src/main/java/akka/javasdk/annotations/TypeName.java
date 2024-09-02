/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to assign a logical type name to events.
 *
 * The type name is used for several things, such as to identify each event in order to
 * deliver them to the right event consumers.
 * If a logical type name isn't specified, the fully qualified class name is used.
 *
 * Once an event is persisted, you won't be able to rename your class if no logical type name
 * has been specified, as previously persisted events would have a different identifier.
 * 
 * Therefore, we recommend all event classes use a logical type name.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface TypeName {

  /** Logical type name for the annotated type.
   * If missing (or defined as Empty String), the fully qualified class name will be used.
   */
  String value() default "";
}
