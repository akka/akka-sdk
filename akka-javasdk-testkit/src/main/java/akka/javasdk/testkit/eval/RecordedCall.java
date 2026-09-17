/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One tool call as recorded in production: the tool, the arguments the agent passed, and the result
 * as recorded JSON. An {@link EvalCase} derived from a recording carries its calls, and {@link
 * ExperimentRunner} loads each result into the stub bound to the tool before the turn.
 */
public record RecordedCall(String tool, Map<String, Object> arguments, String resultJson) {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  public RecordedCall {
    if (tool == null || tool.isBlank()) throw new IllegalArgumentException("tool required");
    if (arguments == null) throw new IllegalArgumentException("arguments required");
    if (resultJson == null) throw new IllegalArgumentException("resultJson required");
    // Not Map.copyOf: a recording may carry null for an argument.
    arguments = Collections.unmodifiableMap(new LinkedHashMap<>(arguments));
  }

  /** The recorded value of one argument, or null when the call did not carry it. */
  public Object argument(String name) {
    return arguments.get(name);
  }

  /** The recorded result, read into the given type. */
  public <T> T resultAs(Class<T> type) {
    try {
      return MAPPER.readValue(resultJson, type);
    } catch (Exception e) {
      throw new IllegalArgumentException(
          "recorded result of "
              + tool
              + " does not read as "
              + type.getSimpleName()
              + ": "
              + resultJson,
          e);
    }
  }
}
