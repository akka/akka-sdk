/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.impl

import akka.javasdk.Tracing
import io.opentelemetry.api.trace.Span

import java.util.Optional

/**
 * INTERNAL API
 */
object TestKitTracing extends Tracing {

  override def startSpan(name: String): Optional[Span] = Optional.empty()

}
