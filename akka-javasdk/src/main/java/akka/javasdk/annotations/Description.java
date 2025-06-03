/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.annotations;

import java.lang.annotation.*;

/**
 * Used to define human language description of an MCP tool parameter field for the MCP endpoint.
 */
// FIXME share field/parameter description metadata with other parts of the SDK API for agents
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Description {
  String value();
}
