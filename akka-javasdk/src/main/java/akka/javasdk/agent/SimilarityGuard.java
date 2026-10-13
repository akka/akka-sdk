/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

/**
 * The SimilarityGuard evaluates the text by making a similarity search in a dataset of "bad
 * examples". If the similarity reaches the threshold, SimilarityGuard blocks the result.
 *
 * @deprecated SimilarityGuard is deprecated for every {@code use-for} value. For {@code
 *     model-request} and {@code mcp-tool-response}, use {@link ModelCallSimilarityGuard}. The SDK
 *     has no built-in replacement for {@code model-response} or {@code mcp-tool-request}.
 */
@Deprecated(since = "3.7.0", forRemoval = true)
@SuppressWarnings("removal")
public final class SimilarityGuard implements TextGuardrail {
  private final String badExamplesResourceDir;
  private final double threshold;

  /** Reads {@code bad-examples-resource-dir} and {@code threshold} from the guardrail's config. */
  public SimilarityGuard(GuardrailContext context) {
    this.badExamplesResourceDir = context.config().getString("bad-examples-resource-dir");
    this.threshold = context.config().getDouble("threshold");
  }

  /** SimilarityGuard blocks text at or above this score. */
  public double threshold() {
    return threshold;
  }

  /** The classpath resource directory holding the "bad examples" dataset. */
  public String badExamplesResourceDir() {
    return badExamplesResourceDir;
  }

  @Override
  public Result evaluate(String text) {
    throw new IllegalStateException("Not expected to be called");
  }
}
