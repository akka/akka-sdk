/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

import akka.annotation.DoNotInherit;
import akka.grpc.javadsl.AkkaGrpcClient;

/**
 * Registry of stubbed gRPC services for a running {@link TestKit}. Lets individual tests install or
 * replace stub instances without re-creating the testkit.
 *
 * <p>Entries declared via {@link TestKit.Settings#withStubbedGrpcService(String, Class,
 * AkkaGrpcClient)} are used to seed the registry at testkit startup and are restored by {@link
 * #reset()}.
 *
 * <p>Not for user extension.
 */
@DoNotInherit
public interface StubbedGrpcServices {

  /**
   * Register or replace a stub instance for calls made through {@code
   * grpcClientProvider.grpcClientFor(serviceClass, serviceName)}.
   */
  <T extends AkkaGrpcClient> void stubResponse(
      String serviceName, Class<T> serviceClass, T stubInstance);

  /** Remove the stub for the given service name and client class, if any. */
  <T extends AkkaGrpcClient> void remove(String serviceName, Class<T> serviceClass);

  /** Reset the registry back to the stubs declared in {@link TestKit.Settings}. */
  void reset();
}
