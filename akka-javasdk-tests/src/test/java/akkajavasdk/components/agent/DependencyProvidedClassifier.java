/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.Classification;
import akka.javasdk.agent.Classifier;
import akka.javasdk.agent.ClassifierContext;
import akkajavasdk.protocol.TestGrpcServiceClient;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Test classifier whose constructor takes a dependency resolvable only via the service's {@code
 * DependencyProvider} ({@link akkajavasdk.components.Bootstrap#createDependencyProvider()}), not
 * via any platform-managed inject. Proves classifier construction happens after {@code
 * ServiceSetup.createDependencyProvider()} has run.
 */
public class DependencyProvidedClassifier implements Classifier {
  private final TestGrpcServiceClient dependencyProvidedClient;

  public DependencyProvidedClassifier(
      ClassifierContext context, TestGrpcServiceClient dependencyProvidedClient) {
    this.dependencyProvidedClient = dependencyProvidedClient;
  }

  @Override
  public CompletionStage<Classification> classify(String input) {
    return CompletableFuture.completedFuture(
        Classification.label(dependencyProvidedClient != null ? "resolved" : "missing"));
  }
}
