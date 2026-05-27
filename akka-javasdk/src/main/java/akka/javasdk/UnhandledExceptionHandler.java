/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk;

import akka.javasdk.annotations.Setup;

/**
 * Mix-in interface that can be implemented alongside {@link ServiceSetup} on a class annotated with
 * {@link Setup} to be notified when the runtime catches an exception thrown by user code from a
 * component or endpoint.
 *
 * <p>Typical use is forwarding to an external error tracker.
 *
 * <p>The callback is invoked on the SDK dispatcher. It must not throw — any exception it throws is
 * logged and swallowed.
 *
 * <p>Example:
 *
 * <pre>{@code
 * @Setup
 * public class Bootstrap implements ServiceSetup, UnhandledExceptionHandler {
 *   @Override
 *   public void onUnhandledException(UnhandledExceptionContext context) {
 *     Sentry.captureException(context.throwable());
 *   }
 * }
 * }</pre>
 */
public interface UnhandledExceptionHandler {

  /**
   * Invoked when the runtime catches an exception thrown by user code that was not turned into a
   * {@link CommandException} or a deliberate HTTP error response.
   *
   * @param context the exception together with the correlation id surfaced to the client
   */
  void onUnhandledException(UnhandledExceptionContext context);
}
