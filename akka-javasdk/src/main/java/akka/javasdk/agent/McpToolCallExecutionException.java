/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

/**
 * Exception thrown when there is a failure executing an MCP (Model Context Protocol) tool call.
 * This includes the name of the tool and the endpoint that failed.
 */
final public class McpToolCallExecutionException extends RuntimeException {

  private final String toolName;
  private final String endpoint;

  public McpToolCallExecutionException(String message, String toolName, String endpoint, Throwable cause) {
    super(message, cause);
    this.toolName = toolName;
    this.endpoint = endpoint;
  }

  /**
   * Returns the name of the MCP tool that failed to execute.
   * @return the tool name
   */
  public String getToolName() {
    return toolName;
  }

  /**
   * Returns the MCP endpoint where the failure occurred.
   * @return the endpoint
   */
  public String getEndpoint() {
    return endpoint;
  }
}
