/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import akka.annotation.InternalApi
import akka.javasdk.agent.ModelGuardrailContext

/**
 * INTERNAL API
 */
@InternalApi private[javasdk] final class ModelGuardrailContextImpl(val text: String) extends ModelGuardrailContext
