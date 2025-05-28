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
  /**
   * @return A unique URI identifying the resource.
   */
  String uri();

  /**
   * @return A human-readable name for this resource. Clients can use this to populate UI elements, for example.
   */
  String name();

  /**
   * @return A description of what this resource represents. Clients can use this to improve the LLM's
   *         understanding of available resources. It can be thought of as a "hint" to the model.
   */
  String description() default  "";

  /**
   * @return The MIME type of this resource. If not defined, String output will be presented as {@code text/plain} and
   *         byte arrays as {@code ???}
   */
  String mimeType() default "";
}
