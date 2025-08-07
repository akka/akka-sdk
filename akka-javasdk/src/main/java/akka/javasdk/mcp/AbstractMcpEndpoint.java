/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.mcp;

import akka.annotation.InternalApi;

/**
 * Optional base class for MCP (Model Context Protocol) endpoints providing convenient access to
 * request context.
 *
 * <p>MCP endpoints expose services to MCP clients such as LLM chat agent desktop applications and
 * agents running on other services. They can provide tools, resources, and prompts that AI models
 * can use to extend their capabilities.
 *
 * <p><strong>MCP Capabilities:</strong>
 *
 * <ul>
 *   <li><strong>Tools:</strong> Functions/logic that MCP clients can call on behalf of the LLM
 *   <li><strong>Resources:</strong> Static resources or dynamic resource templates that clients can
 *       fetch
 *   <li><strong>Prompts:</strong> Template prompts created from input parameters
 * </ul>
 *
 * <p><strong>Basic Usage:</strong>
 *
 * <pre>{@code
 * @Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
 * @McpEndpoint(
 *     serverName = "my-service-mcp",
 *     serverVersion = "1.0.0")
 * public class MyMcpEndpoint extends AbstractMcpEndpoint {
 *
 *   @McpTool(description = "Adds two numbers")
 *   public String add(@Description("First number") int a, @Description("Second number") int b) {
 *     return String.valueOf(a + b);
 *   }
 * }
 * }</pre>
 *
 * <p><strong>Request Context:</strong> Extending this class provides access to {@link
 * McpRequestContext} via {@link #requestContext()} without requiring constructor injection. The
 * context provides access to request headers, JWT claims, principals, and tracing information.
 *
 * <p><strong>Alternative Approach:</strong> Instead of extending this class, you can inject {@link
 * McpRequestContext} directly into your endpoint constructor or use dependency injection for other
 * services like {@link akka.javasdk.client.ComponentClient}.
 *
 * <p>MCP endpoints are made available using a stateless streamable HTTP transport as defined by the
 * MCP specification. By default, endpoints are served at the {@code /mcp} path.
 */
public abstract class AbstractMcpEndpoint {

  private volatile McpRequestContext context;

  /**
   * INTERNAL API
   *
   * @hidden
   */
  @InternalApi
  public final void _internalSetRequestContext(McpRequestContext context) {
    this.context = context;
  }

  /** Always available from request handling methods, not available from the constructor. */
  protected final McpRequestContext requestContext() {
    if (context == null) {
      throw new IllegalStateException(
          "The request context can only be accessed from the request handling methods of the"
              + " endpoint.");
    }
    return context;
  }
}
