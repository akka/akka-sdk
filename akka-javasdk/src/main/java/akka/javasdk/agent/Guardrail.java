/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

public interface Guardrail {
  enum Category {
    JAILBREAK,
    PROMPT_INJECTION,
    PII,
    TOXIC,
    HALLUCINATED,
    NSFW,
    FORMAT,
    OTHER
  }

  record Result(boolean passed, String reason) {}

  /**
   * Thrown when the text didn't pass the evaluation criteria, and {@code abortExecution} is true.
   * Can be handled in {@code onFailure}.
   */
  final class GuardrailException extends RuntimeException {
    public GuardrailException(String message) {
      super(message);
    }
  }

  Result evaluate(String text);

  Category category();

  /** Custom {@code Category.OTHER} category that isn't in the predefined types. */
  default String otherCategory() {
    return "other";
  }

  /** Name that describes the usage of the guardrail. */
  String name();

  /**
   * If the text didn't pass the evaluation criteria, the execution can either be aborted by
   * throwing {@code GuardrailException} or continue anyway. In both cases, the result is tracked in
   * logs, metrics and traces.
   */
  default boolean reportOnly() {
    return false;
  }
}
