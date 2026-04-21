/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

import akka.annotation.DoNotInherit;
import akka.grpc.javadsl.AkkaGrpcClient;

/**
 * Registry of mocked gRPC services for a running {@link TestKit}. Lets individual tests install or
 * replace mock instances without re-creating the testkit.
 *
 * <p>Entries declared via {@link TestKit.Settings#withMockedGrpcService(String, Class,
 * AkkaGrpcClient)} are used to seed the registry at testkit startup and are restored by {@link
 * #reset()}.
 *
 * <p>The mock instance is shared across calls and method invocations can overlap when the service
 * under test issues concurrent requests, so any state shared between the mock and the test class
 * (captured fields, counters, queues) must be thread-safe.
 *
 * <p>Not for user extension.
 */
@DoNotInherit
public interface MockedGrpcServices {

  /**
   * Register or replace a mock instance for calls made through {@code
   * grpcClientProvider.grpcClientFor(serviceClass, serviceName)}.
   */
  <T extends AkkaGrpcClient> void mockResponse(
      String serviceName, Class<T> serviceClass, T mockInstance);

  /** Remove the mock for the given service name and client class, if any. */
  <T extends AkkaGrpcClient> void remove(String serviceName, Class<T> serviceClass);

  /** Reset the registry back to the mocks declared in {@link TestKit.Settings}. */
  void reset();
}
