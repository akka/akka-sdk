/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import akka.javasdk.Tracing;

/**
 * Per-call context passed to a {@link ModelGuardrail} during {@link ModelGuardrail#evaluate}.
 *
 * <p>Carries data about the specific model call being evaluated.
 *
 * <p>For construction-time data that doesn't change per call (the guardrail's configured name and
 * its config section) accept a {@link GuardrailContext} parameter in the constructor.
 */
public interface ModelGuardrailContext {

  /**
   * The text being evaluated: the user input when evaluating a model request, the model output when
   * evaluating a model response.
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
