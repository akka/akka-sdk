/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

/**
 * Guardrails can protect against harmful inputs and outputs to/from model and tool calls.
 *
 * <p>A Guardrail needs to implement {@link ToolGuardrail} or {@link ModelGuardrail}, which extend
 * this interface, have a public constructor optionally taking a {@link GuardrailContext} parameter
 * (the guardrail's configured name and config section).
 *
 * <p>Guardrails are enabled for agents with configuration, see agent documentation.
 */
@SuppressWarnings("removal")
public sealed interface Guardrail permits TextGuardrail, ToolGuardrail, ModelGuardrail {

  /**
   * The result of the guardrail evaluation.
   *
   * @param passed true if the text passed the guardrail evaluation
   * @param explanation reason for the decision, especially when it didn't pass
   * @deprecated Use {@link Decision} from {@link ToolGuardrail} or {@link ModelGuardrail}.
   */
  @Deprecated(since = "3.6.0", forRemoval = true)
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
