/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

/**
 * A guardrail that evaluates model-side calls (for example, model requests or agent responses).
 *
 * <p>An implementation has a public constructor, optionally taking a {@link GuardrailContext}
 * parameter, which gives access to the guardrail's configured name and config section. The per-call
 * data is delivered to {@link #evaluate} via {@link ModelGuardrailContext}. Guardrails are enabled
 * and bound to boundaries via configuration; see the agent documentation.
 *
 * <p>Returning {@link Decision#allow()} lets the call proceed. Returning a {@link Decision.Deny}
 * stops it with the supplied reason. Returning a {@link Decision.Error} signals the guardrail could
 * not reach a verdict because evaluation itself failed.
 */
public non-sealed interface ModelGuardrail extends Guardrail {

  /** Evaluates the call described by {@code ctx} and returns a {@link Decision}. */
  Decision evaluate(ModelGuardrailContext ctx);
}
