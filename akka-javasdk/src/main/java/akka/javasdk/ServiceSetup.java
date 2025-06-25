/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk;

import akka.javasdk.annotations.Setup;

import java.util.Set;

/**
 * Implement this on a single class per deployable service annotated with {{@link Setup}} and
 * override one or more of the default methods to provide objects for dependency injection or act on
 * service lifecycle events.
 *
 * <p>Concrete classes can accept the following types to the constructor:
 *
 * <ul>
 *   <li>{@link akka.javasdk.client.ComponentClient}
 *   <li>{@link akka.javasdk.http.HttpClientProvider}
 *   <li>{@link akka.javasdk.timer.TimerScheduler}
 *   <li>{@link akka.stream.Materializer}
 *   <li>{@link com.typesafe.config.Config}
 * </ul>
 */
public interface ServiceSetup {

  // FIXME document something about thread safety or that it should not be stateful/mutate concrete

  /**
   * The on startup hook is called every time a service instance boots up. This can happen for very
   * different reasons: restarting / redeploying the service, scaling up to more instances or even
   * without any user-driven action (e.g.: Runtime versions being rolled out, infrastructure-related
   * incidents, etc.). Therefore, one should carefully consider how to use this hook and its
   * implementation.
   *
   * <p>This hook is called after {@link #createDependencyProvider()}.
   */
  default void onStartup() {}

  /**
   * Invoked once before the service is started, to create a dependency provider. It is not possible to
   * interact with components in this method.
   *
   * <p>This hook is called before {@link #onStartup()}.
   */
  default DependencyProvider createDependencyProvider() {
    return null;
  }

  /**
   * Provides a set of components that should be disabled from running.
   */
  default Set<Class<?>> disabledComponents() {
    return Set.of();
  }
}
