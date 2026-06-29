/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import akka.annotation.InternalApi
import akka.javasdk.Tracing
import akka.javasdk.agent.ModelGuardrailContext
import akka.javasdk.impl.telemetry.SpanTracingImpl
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.{ Context => OtelContext }

/**
 * INTERNAL API
 */
@InternalApi private[javasdk] final class ModelGuardrailContextImpl(
    val text: String,
    telemetryContext: Option[OtelContext],
    tracerFactory: () => Tracer)
    extends ModelGuardrailContext {

  override def tracing(): Tracing = new SpanTracingImpl(telemetryContext, tracerFactory)
}
