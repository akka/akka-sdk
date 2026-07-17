/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.Classification;
import akka.javasdk.agent.Classifier;
import akka.javasdk.agent.ClassifierContext;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Test classifier: labels input containing "toxic" as toxic with score 1.0, everything else as
 * clean with score 0.0. Overrides the async {@link #classifyAsync} variant (the poweruser shape),
 * completing on a separate thread to exercise an async classifier invoked through both client
 * methods.
 */
public class ThresholdClassifier implements Classifier {
  private final double threshold;

  public ThresholdClassifier(ClassifierContext context) {
    this.threshold = context.config().getDouble("threshold");
  }

  public double threshold() {
    return threshold;
  }

  @Override
  public Classification classify(String input) {
    throw new UnsupportedOperationException(
        "sync classify must not be called, classifyAsync is overridden");
  }

  @Override
  public CompletionStage<Classification> classifyAsync(String input) {
    return CompletableFuture.supplyAsync(
        () -> {
          boolean toxic = input.toLowerCase().contains("toxic");
          return toxic ? Classification.of(1.0, "toxic") : Classification.of(0.0, "clean");
        });
  }
}
