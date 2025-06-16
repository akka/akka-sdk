/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.annotations;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// test annotation (written in java) to test Reflect.valueOrAlias
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface OptionalDescription {

  String value() default "";

  @ValueAlias(required = false)
  String description() default "";
}
