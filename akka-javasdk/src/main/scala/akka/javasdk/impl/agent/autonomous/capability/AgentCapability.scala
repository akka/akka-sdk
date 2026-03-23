/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent.autonomous.capability

import akka.annotation.InternalApi

/**
 * INTERNAL API
 *
 * Common trait for all agent capability types. Capabilities are stored in the agent definition or setup and converted
 * to SPI types by [[akka.javasdk.impl.agent.autonomous.CapabilityConverter]].
 */
@InternalApi
private[javasdk] trait AgentCapability
