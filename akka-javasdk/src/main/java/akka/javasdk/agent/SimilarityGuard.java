/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

/**
 * The SimilarityGuard evaluates the text by making a similarity search in a dataset of "bad
 * examples". If the similarity reaches the threshold, SimilarityGuard blocks the result.
 *
 * @deprecated If this class uses {@code use-for = ["model-request"]}, use {@link
 *     ModelCallSimilarityGuard} instead. {@code mcp-tool-request} and {@code mcp-tool-response}
 *     have no replacement.
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
