/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.annotations.mcp;


import java.lang.annotation.*;

/**
 * Marks a method that returns an MCP resource that clients can fetch.
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
