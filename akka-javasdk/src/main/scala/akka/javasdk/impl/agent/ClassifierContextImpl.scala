/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import akka.annotation.InternalApi
import akka.javasdk.agent.ClassifierClient
import akka.javasdk.agent.ClassifierContext
import com.typesafe.config.Config

/**
 * INTERNAL API
 */
@InternalApi private[javasdk] final class ClassifierContextImpl(
    override val name: String,
    override val config: Config,
    override val classifierClient: ClassifierClient)
    extends ClassifierContext
