/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import akka.Done
import akka.annotation.InternalApi
import akka.runtime.sdk.spi.SpiUnhandledException
import org.slf4j.Logger

/**
 * INTERNAL API
 *
 * Stateless components (consumers, timed actions) catch exceptions thrown by user code and turn them into a successful
 * effect, so that failure is reported through the component's own error/retry protocol rather than failing the runtime
 * call. That means the exception never reaches the runtime as a failed `Future`, so the runtime's own
 * unhandled-exception reporting (triggered from failed Futures) never fires for it. These components must report
 * directly instead, using the same `onUnhandledException` callback the runtime would otherwise invoke.
 */
@InternalApi
private[impl] object UnhandledExceptionReporting {

  type Reporter = SpiUnhandledException => Future[Done]

  def report(
      reporter: () => Option[Reporter],
      throwable: Throwable,
      correlationId: String,
      componentId: String,
      componentClassName: String,
      log: Logger)(implicit ec: ExecutionContext): Unit =
    reporter().foreach { doReport =>
      doReport(new SpiUnhandledException(throwable, correlationId, None, componentId, componentClassName)).failed
        .foreach(ex => log.warn("Unhandled exception reporter failed", ex))
    }

}
