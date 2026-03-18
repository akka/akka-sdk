/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.annotation.InternalApi
import akka.runtime.sdk.spi.SpiPolicyDescriptor

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] final case class PolicyDescriptorImpl(name: String, version: String, basePath: String)
    extends SpiPolicyDescriptor
