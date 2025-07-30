/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

/**
 * Exception thrown when an MCP (Model Context Protocol) tool call fails during execution.
 *
 * <p>This exception is thrown by agents when they attempt to call MCP tools from remote servers and
 * the tool execution fails. It provides detailed information about which tool and endpoint failed
 * to help with debugging and error handling.
 *
 * <p><strong>Context Information:</strong> The exception includes the tool name and endpoint URL to
 * help identify the source of the failure. This is particularly useful when agents are using
 * multiple MCP servers or when debugging tool integration issues.
 *
 * <p><strong>Error Handling:</strong> Agents can catch this exception in their {@code onFailure}
 * handlers to provide fallback behavior or alternative responses when MCP tools are unavailable.
 */
public final class McpToolCallExecutionException extends RuntimeException {

  private final String toolName;
  private final String endpoint;

  public McpToolCallExecutionException(
      String message, String toolName, String endpoint, Throwable cause) {
    super(message, cause);
    this.toolName = toolName;
    this.endpoint = endpoint;
  }

  /**
   * Returns the name of the MCP tool that failed to execute.
   *
   * @return the tool name
   */
  public String getToolName() {
    return toolName;
  }

  /**
   * Returns the MCP endpoint where the failure occurred.
   *
   * @return the endpoint
   */
  public String getEndpoint() {
    return endpoint;
  }
}
