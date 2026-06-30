/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

/**
 * A guardrail that evaluates model-side calls (for example, model requests or agent responses).
 *
 * <p>An implementation has a public constructor, optionally taking a {@link GuardrailContext}
 * parameter, which gives access to the guardrail's configured name and config section. The per-call
 * data is delivered to {@link #decide} via {@link ModelGuardrailContext}. Guardrails are enabled
 * and bound to boundaries via configuration; see the agent documentation.
 */
public non-sealed interface ModelGuardrail extends Guardrail {

  /** Evaluates the call described by {@code ctx} and returns a {@link Decision}. */
  // FIXME: should become asynchronous and return CompletionStage<Decision>. User code inside the
  //  guardrail may run async work (e.g. calling external or internal components) and we don't
  //  control its threading. The SPI Guardrail.evaluate is already a Future; align this with it.
  Decision decide(ModelGuardrailContext ctx);
}
