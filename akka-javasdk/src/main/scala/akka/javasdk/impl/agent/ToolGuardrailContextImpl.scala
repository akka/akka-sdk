/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import akka.annotation.InternalApi
import akka.javasdk.Tracing
import akka.javasdk.agent.ToolGuardrailContext
import akka.javasdk.impl.telemetry.SpanTracingImpl
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.{ Context => OtelContext }

/**
 * INTERNAL API
 */
@InternalApi private[javasdk] final class ToolGuardrailContextImpl(
    val agentId: String,
    val toolName: String,
    val toolCallId: String,
    val arguments: String,
    val sessionId: String,
    telemetryContext: Option[OtelContext],
    tracerFactory: () => Tracer)
    extends ToolGuardrailContext {

  override def tracing(): Tracing = new SpanTracingImpl(telemetryContext, tracerFactory)
}
