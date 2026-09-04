/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

import java.util.Map;
import java.util.Optional;

/**
 * One tool call: the name, the deserialized arguments and, when recorded, the result or the error.
 *
 * <p>Names are the plain method names, without the agent class prefix the model sees ({@code
 * SupportAgent_getCustomer}).
 *
 * @param arguments by parameter name
 * @param result the tool's result as the model saw it, when recorded
 * @param error the failure message when the tool threw
 */
public record ToolCall(
    String name, Map<String, Object> arguments, Optional<String> result, Optional<String> error) {

  public ToolCall {
    if (name == null || name.isBlank()) throw new IllegalArgumentException("tool name required");
    arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
    result = result == null ? Optional.empty() : result;
    error = error == null ? Optional.empty() : error;
  }

  /** A call with no recorded result. */
  public ToolCall(String name, Map<String, Object> arguments) {
    this(name, arguments, Optional.empty(), Optional.empty());
  }

  public static ToolCall of(String name) {
    return new ToolCall(name, Map.of());
  }

  public boolean failed() {
    return error.isPresent();
  }
}
