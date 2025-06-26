/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

/**
 * Thrown when the maximum number of tool call steps has been reached.
 * Indicates that too many tool calls were made within a single request/response cycle.
 * <p>
 * You can configure this limit in the `application.conf` file using the setting {@code akka.javasdk.agent.max-tool-call-steps}.
 */
final public class ToolCallLimitReachedException extends RuntimeException {

  public ToolCallLimitReachedException(String message) {
    super(message);
  }
}
