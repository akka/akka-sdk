/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.annotation.InternalApi
import akka.javasdk.SanitizerContext
import com.typesafe.config.Config

/**
 * INTERNAL API
 */
@InternalApi private[javasdk] final class SanitizerContextImpl(override val name: String, override val config: Config)
    extends SanitizerContext
