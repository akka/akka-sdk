/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import akka.javasdk.Tracing;

/**
 * A guardrail that evaluates model-side calls (for example, model requests or agent responses).
 *
 * <p>An implementation has a public constructor, optionally taking a {@link GuardrailContext}
 * parameter, which gives access to the guardrail's configured name and config section. The per-call
 * data is delivered to {@link #decide} via {@link CallContext}. Guardrails are enabled and bound to
 * boundaries via configuration; see the agent documentation.
 */
public non-sealed interface ModelGuardrail extends Guardrail {

  /**
   * Per-call context passed to a {@link ModelGuardrail} during {@link ModelGuardrail#decide}.
   *
   * <p>Carries data about the specific model call being evaluated.
   *
   * <p>For construction-time data that doesn't change per call (the guardrail's configured name and
   * its config section) accept a {@link GuardrailContext} parameter in the constructor.
   */
  public interface CallContext {

    /**
     * The text being evaluated: the user input when evaluating a model request, the model output
     * when evaluating a model response.
     */
    String text();

    /**
     * Provides access to tracing for custom application-specific tracing.
     *
     * <p>Spans started through this are parented to the model call being evaluated, so work the
     * guardrail performs (e.g. calling external or internal components) shows up under the
     * interaction's trace.
     *
     * @return tracing interface for custom tracing
     */
    Tracing tracing();
  }

  /** Evaluates the call described by {@code ctx} and returns a {@link Decision}. */
  // FIXME: should become asynchronous and return CompletionStage<Decision>. User code inside the
  //  guardrail may run async work (e.g. calling external or internal components) and we don't
  //  control its threading. The SPI Guardrail.evaluate is already a Future; align this with it.
  Decision decide(CallContext ctx);
}
