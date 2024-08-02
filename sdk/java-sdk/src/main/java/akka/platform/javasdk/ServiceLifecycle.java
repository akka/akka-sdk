/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk;

public interface ServiceLifecycle {
  // FIXME possible additional hooks
  //   preStart() to delay runtime start until some preparation is complete with posibility to
  //              share the result of that preparation with components somehow
  //  onShutdown() no obvious use case but for consistency it could make sense?
  /**
   * The on startup hook is called every time a service instance boots up. This can happen for very
   * different reasons: restarting / redeploying the service, scaling up to more instances or even
   * without any user-driven action (e.g. Kalix Runtime versions being rolled out,
   * infrastructure-related incidents, etc.). Therefore, one should carefully consider how to use
   * this hook and its implementation.
   */
  // FIXME I simply skipped the retry part from the existing trigger, I don't quite see why it makes
  // sense that it is
  //       a concern for the SDK/runtime calling the hook but rather belongs in user logic inside of
  // the hook.
  // FIXME possible useful params to the hooks: actor system, config, an enum with DEV/TEST/PROD
  // (like play?)
  default void onStartup() {}
}
