/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

import akka.annotation.DoNotInherit;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import java.util.function.Function;

/**
 * Registry of mocked HTTP services for a running {@link TestKit}. Lets individual tests install or
 * replace mock handlers without re-creating the testkit.
 *
 * <p>Handlers run synchronously on the SDK dispatcher (virtual threads); blocking in the handler is
 * safe. Invocations can overlap when the service under test issues concurrent requests, so any
 * state shared between the handler and the test class (captured fields, counters, queues) must be
 * thread-safe.
 *
 * <p>Entries declared via {@link TestKit.Settings#withMockedHttpService(String, Function)} are used
 * to seed the registry at testkit startup and are restored by {@link #reset()}.
 *
 * <p>Not for user extension.
 */
@DoNotInherit
public interface MockedHttpServices {

  /**
   * Register or replace a mock handler for calls made through {@code
   * httpClientProvider.httpClientFor(serviceName)}.
   */
  void mockResponse(String serviceName, Function<HttpRequest, HttpResponse> handler);

  /** Remove the mock for the given service name, if any. */
  void remove(String serviceName);

  /** Reset the registry back to the mocks declared in {@link TestKit.Settings}. */
  void reset();
}
