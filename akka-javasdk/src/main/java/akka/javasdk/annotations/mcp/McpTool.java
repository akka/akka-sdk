/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.annotations.mcp;

import java.lang.annotation.*;

/**
 * Marks a public method on a {@link McpEndpoint} a tool that can be called by MCP clients.
 * <p>
 * The method may accept 0 or more parameters, but must return a {@code String}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpTool {

  /**
   * @return The name of the tool. Must be unique in the same MCP endpoint if specified, if not specified, the method
   *         name is used.
   */
  String name() default "";

  /**
   * @return A clear description of what the tools, used by the client LLM to determine what the tool can be used for
   */
  String description();

  /**
   * Normally, the schema is inferred from the input parameters of the tool method.
   * @return A manually specified schema instead of the automatic must match the input parameters and how they are
   *         parsed by Jackson.
   */
  String inputSchema() default "";

  /**
   * Optional annotations describing what the tool does to the client.
   */
  ToolAnnotation[] annotations() default {};
}
