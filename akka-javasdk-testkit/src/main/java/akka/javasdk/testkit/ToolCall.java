/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * One tool call: the name, the deserialized arguments and, when recorded, the result or the error.
 *
 * <p>Names are the plain method names, without the agent class prefix the model sees ({@code
 * SupportAgent_getCustomer}).
 *
 * @param arguments by parameter name. A value is null when the model sent null
 * @param result the tool's result as the model saw it, when recorded
 * @param error the failure message when the tool threw or a guardrail denied the call
 * @param deniedBy the name of the tool-call guardrail that denied the call. The tool did not run
 */
public record ToolCall(
    String name,
    Map<String, Object> arguments,
    Optional<String> result,
    Optional<String> error,
    Optional<String> deniedBy) {

  public ToolCall {
    if (name == null || name.isBlank()) throw new IllegalArgumentException("tool name required");
    // Not Map.copyOf: a model may send null for an argument.
    arguments =
        arguments == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(arguments));
    result = result == null ? Optional.empty() : result;
    error = error == null ? Optional.empty() : error;
    deniedBy = deniedBy == null ? Optional.empty() : deniedBy;
  }

  /** A call no guardrail denied. */
  public ToolCall(
      String name, Map<String, Object> arguments, Optional<String> result, Optional<String> error) {
    this(name, arguments, result, error, Optional.empty());
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

  /** Whether a tool-call guardrail denied the call. */
  public boolean denied() {
    return deniedBy.isPresent();
  }
}
