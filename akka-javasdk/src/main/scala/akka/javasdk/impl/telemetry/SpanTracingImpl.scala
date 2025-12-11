/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.telemetry

import java.util.Optional

import scala.jdk.OptionConverters.RichOption

import akka.annotation.InternalApi
import akka.javasdk.Tracing
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.{ Context => OtelContext }

/**
 * INTERNAL API
 */
@InternalApi
final class SpanTracingImpl(val context: Option[OtelContext], tracerFactory: () => Tracer) extends Tracing {
  override def startSpan(name: String): Optional[Span] =
    context.map { parent =>
      tracerFactory()
        .spanBuilder(name)
        .setParent(parent)
        .startSpan()
    }.toJava

  override def parentSpan(): Optional[Span] =
    context.flatMap(context => Option(Span.fromContextOrNull(context))).toJava
}
