/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.impl

import java.util.Optional

import akka.javasdk.Tracing
import io.opentelemetry.api.trace.Span

/**
 * INTERNAL API
 */
object TestKitTracing extends Tracing {

  override def startSpan(name: String): Optional[Span] = Optional.empty()

  override def parentSpan(): Optional[Span] = Optional.empty()
}
