/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk;

/**
 * Implement this on a single class per deployable service annotated with {{@link
 * akka.platform.javasdk.annotations.PlatformServiceSetup}} and override one or more of the default
 * methods to provide objects for dependency injection or act on service lifecycle events.
 *
 * <p>The constructor of the concrete class can get {@link
 * akka.platform.javasdk.timer.TimerScheduler}, {@link
 * akka.platform.javasdk.client.ComponentClient}, and {@link com.typesafe.config.Config} injected to
 * for example allow on-startup scheduling of calls.
 */
// FIXME possibly allow additional useful constructor params to inject: config, an enum with
// DEV/TEST/PROD
public interface ServiceSetup {

  /**
   * The on startup hook is called every time a service instance boots up. This can happen for very
   * different reasons: restarting / redeploying the service, scaling up to more instances or even
   * without any user-driven action (e.g. Kalix Runtime versions being rolled out,
   * infrastructure-related incidents, etc.). Therefore, one should carefully consider how to use
   * this hook and its implementation.
   *
   * <p>The hook is always called before {{@link #createDependencyProvider()}}
   */
  // FIXME document something about thread safety or that it should not be stateful/mutate concrete
  // setup state?
  default void onStartup() {}

  /** Invoked once when the service is starting to create a dependency provider. */
  default DependencyProvider createDependencyProvider() {
    return null;
  }
}
