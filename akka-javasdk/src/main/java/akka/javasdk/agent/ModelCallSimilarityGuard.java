/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import java.util.concurrent.CompletionStage;

/**
 * Built-in {@link ModelCallGuardrail}. Denies a model call when a user message or tool result
 * matches a "bad example" at or above the {@code threshold}.
 *
 * <p>Its config must not define {@code use-for}.
 */
public final class ModelCallSimilarityGuard implements ModelCallGuardrail {

  /**
   * Reads {@code bad-examples-resource-dir} and {@code threshold} from the guardrail's config.
   *
   * @throws IllegalArgumentException if {@code threshold} is not in (0, 1], or if {@code
   *     bad-examples-resource-dir} is not on the classpath
   */
  public ModelCallSimilarityGuard(GuardrailContext context) {
    String badExamplesResourceDir = context.config().getString("bad-examples-resource-dir");
    double threshold = context.config().getDouble("threshold");

    if (getClass().getClassLoader().getResource(badExamplesResourceDir) == null)
      throw new IllegalArgumentException(
          "Guardrail ["
              + context.name()
              + "] bad-examples-resource-dir ["
              + badExamplesResourceDir
              + "] not found on the classpath");

    if (!(threshold > 0.0 && threshold <= 1.0))
      throw new IllegalArgumentException(
          "Guardrail ["
              + context.name()
              + "] threshold must be greater than 0 and at most 1, but was ["
              + threshold
              + "]");
  }

  /** Not supported. Throws {@link IllegalStateException}. */
  @Override
  public Decision decide(CallContext ctx) {
    throw new IllegalStateException(
        "ModelCallSimilarityGuard is evaluated by the runtime and does not support decide");
  }

  /** Not supported. Throws {@link IllegalStateException}. */
  @Override
  public CompletionStage<Decision> decideAsync(CallContext ctx) {
    throw new IllegalStateException(
        "ModelCallSimilarityGuard is evaluated by the runtime and does not support decideAsync");
  }
}
