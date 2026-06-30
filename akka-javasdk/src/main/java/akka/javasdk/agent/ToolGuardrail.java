/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import akka.javasdk.Tracing;

/**
 * A guardrail that evaluates a tool call before it is dispatched (the before-tool-call boundary).
 *
 * <p>An implementation has a public constructor, optionally taking a {@link GuardrailContext}
 * parameter, which gives access to the guardrail's configured name and config section. The per-call
 * data is delivered to {@link #decide} via {@link CallContext}. Guardrails are enabled and bound to
 * boundaries via configuration; see the agent documentation.
 */
public non-sealed interface ToolGuardrail extends Guardrail {

  /**
   * Per-call context passed to a {@link ToolGuardrail} during {@link ToolGuardrail#decide}.
   *
   * <p>Carries data about the specific tool call being evaluated.
   *
   * <p>For construction-time data that doesn't change per call (the guardrail's configured name and
   * its config section) accept a {@link GuardrailContext} parameter in the constructor.
   */
  public interface CallContext {

    /** The id of the agent performing the tool call. */
    String agentId();

    /** The name of the tool about to be called. */
    String toolName();

    /** The id of the tool call, correlating it with the model's tool-call request. */
    String toolCallId();

    /** The raw JSON arguments the model produced for the tool call. */
    String arguments();

    /** The session id of the interaction. */
    String sessionId();

    /**
     * Provides access to tracing for custom application-specific tracing.
     *
     * <p>Spans started through this are parented to the tool call being evaluated, so work the
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
