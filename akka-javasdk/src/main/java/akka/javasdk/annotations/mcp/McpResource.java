/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.annotations.mcp;


import java.lang.annotation.*;

/**
 * Marks a method that returns an MCP resource that clients can fetch.
 * <p>
 * Annotated method must have no parameters and return either {@code String} or {@code byte[]}
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpResource {
  String uri();
  String name();
  String description();
  String mimeType();
}
