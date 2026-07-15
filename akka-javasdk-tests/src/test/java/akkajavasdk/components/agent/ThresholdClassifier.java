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
 * clean with score 0.0. Completes on a separate thread (rather than the calling thread) to exercise
 * the case of an async classifier invoked from a still-synchronous guardrail.
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
  public CompletionStage<Classification> classify(String input) {
    return CompletableFuture.supplyAsync(
        () -> {
          boolean toxic = input.toLowerCase().contains("toxic");
          return toxic ? Classification.of(1.0, "toxic") : Classification.of(0.0, "clean");
        });
  }
}
