/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.annotations.mcp;

import java.lang.annotation.*;

/**
 * Marks a public method on a {{@link McpEndpoint}} a tool that can be called by MCP clients
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpTool {
  /**
   * @return The name of the tool. Must be unique in the same MCP endpoint.
   */
  String name();

  /**
   * @return A clear description of what the tools, used by the client LLM to determine what the tool can be used for
   */
  String description();
}
