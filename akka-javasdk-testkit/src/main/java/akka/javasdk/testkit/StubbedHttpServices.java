/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

import akka.annotation.DoNotInherit;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import java.util.function.Function;

/**
 * Registry of stubbed HTTP services for a running {@link TestKit}. Lets individual tests install or
 * replace stub handlers without re-creating the testkit.
 *
 * <p>Handlers run synchronously on the SDK dispatcher (virtual threads); blocking in the handler is
 * safe.
 *
 * <p>Entries declared via {@link TestKit.Settings#withStubbedHttpService(String, Function)} are
 * used to seed the registry at testkit startup and are restored by {@link #reset()}.
 *
 * <p>Not for user extension.
 */
@DoNotInherit
public interface StubbedHttpServices {

  /**
   * Register or replace a stub handler for calls made through {@code
   * httpClientProvider.httpClientFor(serviceName)}.
   */
  void stubResponse(String serviceName, Function<HttpRequest, HttpResponse> handler);

  /** Remove the stub for the given service name, if any. */
  void remove(String serviceName);

  /** Reset the registry back to the stubs declared in {@link TestKit.Settings}. */
  void reset();
}
