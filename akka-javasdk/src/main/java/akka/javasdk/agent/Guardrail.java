/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

/**
 * Guardrails can protect against harmful inputs and outputs to/from model and tool calls.
 *
 * <p>A Guardrail needs to implement {@link TextGuardrail}, which extends this interface, have a
 * public constructor with optionally a {@link GuardrailContext} parameter, which includes the name
 * and the config section for the specific guardrail.
 *
 * <p>Guardrails are enabled for agents with configuration, see agent documentation.
 */
public sealed interface Guardrail permits TextGuardrail {

  /**
   * The result of the guardrail evaluation.
   *
   * @param passed true if the text passed the guardrail evaluation
   * @param explanation reason for the decision, especially when it didn't pass
   */
  record Result(boolean passed, String explanation) {
    public static final Result OK = new Result(true, "");
  }

  /**
   * Thrown when the text didn't pass the evaluation criteria, and {@code report-only} is true. Can
   * be handled in {@code onFailure}.
   */
  final class GuardrailException extends RuntimeException {
    public GuardrailException(String message) {
      super(message);
    }
  }
}
