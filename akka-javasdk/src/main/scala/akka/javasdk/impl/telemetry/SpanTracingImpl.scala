/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.telemetry

import akka.annotation.InternalApi
import akka.javasdk.Tracing
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.{ Context => OtelContext }

import java.util.Optional
import scala.jdk.OptionConverters.RichOption

/**
 * INTERNAL API
 */
@InternalApi
final class SpanTracingImpl(span: Option[Span], tracerFactory: () => Tracer) extends Tracing {
  override def startSpan(name: String): Optional[Span] =
    span.map { s =>
      val parent = OtelContext.current().`with`(s)
      tracerFactory()
        .spanBuilder("ad-hoc span")
        .setParent(parent)
        .startSpan()
    }.toJava

  override def parentSpan(): Optional[Span] = span.toJava
}
