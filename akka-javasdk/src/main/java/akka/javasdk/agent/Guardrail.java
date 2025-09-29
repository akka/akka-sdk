/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

public interface Guardrail {

  record Result(boolean passed, String explanation) {}

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
}
