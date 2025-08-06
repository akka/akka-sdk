/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

/**
 * Exception thrown when there is a failure executing a tool call. This includes the name of the
 * tool that failed to execute.
 */
public final class ToolCallExecutionException extends RuntimeException {

  private final String toolName;

  public ToolCallExecutionException(String message, String toolName, Throwable cause) {
    super(message, cause);
    this.toolName = toolName;
  }

  /**
   * Returns the name of the tool that failed to execute.
   *
   * @return the tool name
   */
  public String getToolName() {
    return toolName;
  }
}
