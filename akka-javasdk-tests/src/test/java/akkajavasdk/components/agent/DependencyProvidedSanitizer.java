/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.TextSanitizer;
import akkajavasdk.protocol.TestGrpcServiceClient;

/**
 * Test sanitizer whose constructor takes a dependency resolvable only via the service's {@code
 * DependencyProvider} ({@link akkajavasdk.components.Bootstrap#createDependencyProvider()}), not
 * via any platform-managed inject. Proves sanitizer construction happens after {@code
 * ServiceSetup.createDependencyProvider()} has run.
 */
public class DependencyProvidedSanitizer implements TextSanitizer {

  private final TestGrpcServiceClient dependencyProvidedClient;

  public DependencyProvidedSanitizer(TestGrpcServiceClient dependencyProvidedClient) {
    this.dependencyProvidedClient = dependencyProvidedClient;
  }

  @Override
  public String sanitize(String text) {
    return dependencyProvidedClient != null ? "resolved" : "missing";
  }
}
