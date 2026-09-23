/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

/**
 * Built-in {@link ModelCallGuardrail}. Denies a model call when a user message or tool result
 * reaches {@code threshold} similarity with an example in the "bad examples" dataset.
 *
 * <p>Its config must not define {@code use-for}.
 */
public final class ModelCallSimilarityGuard implements ModelCallGuardrail {
  private final String badExamplesResourceDir;
  private final double threshold;

  /** Reads {@code bad-examples-resource-dir} and {@code threshold} from the guardrail's config. */
  public ModelCallSimilarityGuard(GuardrailContext context) {
    this.badExamplesResourceDir = context.config().getString("bad-examples-resource-dir");
    this.threshold = context.config().getDouble("threshold");

    if (threshold <= 0.0 || threshold > 1.0)
      throw new IllegalArgumentException(
          "Guardrail ["
              + context.name()
              + "] threshold must be greater than 0 and at most 1, but was ["
              + threshold
              + "]");
  }

  /** The similarity score at or above which the runtime denies a model call. */
  public double threshold() {
    return threshold;
  }

  /** The classpath resource directory holding the "bad examples" dataset. */
  public String badExamplesResourceDir() {
    return badExamplesResourceDir;
  }

  /** Always throws; not meant to be called directly. */
  @Override
  public Decision decide(CallContext ctx) {
    throw new IllegalStateException(
        "ModelCallSimilarityGuard is evaluated by the runtime and does not support decide");
  }
}
