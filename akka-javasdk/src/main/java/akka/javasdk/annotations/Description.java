/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.annotations;

import java.lang.annotation.*;
/**
 * Used to define a human language description for fields and method parameters,
 * such as MCP tool parameters or tool methods.
 * <p>
 * This annotation can be used to provide additional context.
 * Fields and method parameters of type {@code Optional} are considered as non-required.
 */
// FIXME share field/parameter description metadata with other parts of the SDK API for agents
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Description {
  String value();
}
