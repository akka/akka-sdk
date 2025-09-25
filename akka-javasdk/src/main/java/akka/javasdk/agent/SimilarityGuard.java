/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import com.typesafe.config.Config;

/**
 * The SimilarityGuard evaluates the text by making a similarity search in a dataset of "bad
 * examples". If the similarity exceeds a threshold, the result is flagged as blocked.
 */
public final class SimilarityGuard implements Guardrail {
  private final String badExamplesResourceDir;
  private final double threshold;

  public SimilarityGuard(Config config) {
    this.badExamplesResourceDir = config.getString("bad-examples-resource-dir");
    this.threshold = config.getDouble("threshold");
  }

  public double threshold() {
    return threshold;
  }

  public String badExamplesResourceDir() {
    return badExamplesResourceDir;
  }

  @Override
  public Result evaluate(String text) {
    throw new IllegalStateException("Not expected to be called");
  }
}
