/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.mcp;

import akka.annotation.InternalApi;

/**
 * Optional base class for MCP endpoints giving access to a request context without additional constructor parameters
 */
abstract public class AbstractMcpEndpoint {

  volatile private McpRequestContext context;

  /**
   * INTERNAL API
   *
   * @hidden
   */
  @InternalApi
  final public void _internalSetRequestContext(McpRequestContext context) {
    this.context = context;
  }

  /**
   * Always available from request handling methods, not available from the constructor.
   */
  protected final McpRequestContext requestContext() {
    if (context == null) {
      throw new IllegalStateException("The request context can only be accessed from the request handling methods of the endpoint.");
    }
    return context;
  }

}
