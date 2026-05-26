/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

/**
 * Guardrails can protect against harmful inputs and outputs to/from model and tool calls.
 *
 * <p>A Guardrail needs to implement this interface, have a public constructor with optionally a
 * {@link GuardrailContext} parameter, which includes the name and the config section for the
 * specific guardrail.
 *
 * <p>Guardrails are enabled for agents with configuration, see agent documentation.
 *
 * @deprecated Implement {@link ToolGuardrail} or {@link ModelGuardrail} instead. The new interfaces
 *     return a {@link Decision} ({@code Allow} / {@code Deny} / {@code Fail}) and receive a
 *     per-call context.
 */
@Deprecated(since = "3.6.0", forRemoval = true)
@SuppressWarnings("removal")
public non-sealed interface TextGuardrail extends Guardrail {

  /** Evaluates if the text passes the guardrail or not. */
  Result evaluate(String text);
}
