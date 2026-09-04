/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import java.util.Map;
import java.util.Optional;

/**
 * One tool invocation, as evidence: the name the model asked for, the arguments the runtime
 * deserialized for it, and, when the evidence source carries them, what came back.
 *
 * <p>Names are the plain method names, without the class prefix the model sees ({@code
 * SupportAgent_getCustomer}); expectations and recordings use the same convention so the two
 * compare.
 *
 * @param arguments by parameter name; values are what arrived, after deserialization
 * @param result the tool's result as the model saw it, when the source records results
 * @param error the failure message when the tool threw instead of answering
 */
public record ToolCall(
    String name, Map<String, Object> arguments, Optional<String> result, Optional<String> error) {

  public ToolCall {
    if (name == null || name.isBlank()) throw new IllegalArgumentException("tool name required");
    arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
    result = result == null ? Optional.empty() : result;
    error = error == null ? Optional.empty() : error;
  }

  /** A call with no record of what it returned. */
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
