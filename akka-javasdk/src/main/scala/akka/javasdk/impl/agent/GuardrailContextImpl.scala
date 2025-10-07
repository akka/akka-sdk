/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import akka.annotation.InternalApi
import akka.javasdk.agent.GuardrailContext
import com.typesafe.config.Config

/**
 * INTERNAL API
 */
@InternalApi private[javasdk] final class GuardrailContextImpl(override val name: String, override val config: Config)
    extends GuardrailContext
