/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import akka.annotation.InternalApi
import akka.javasdk.agent.ToolGuardrailContext

/**
 * INTERNAL API
 */
@InternalApi private[javasdk] final class ToolGuardrailContextImpl(
    val agentId: String,
    val toolName: String,
    val toolCallId: String,
    val arguments: String,
    val sessionId: String,
    val traceId: String)
    extends ToolGuardrailContext
