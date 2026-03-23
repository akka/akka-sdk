/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent.autonomous.capability

import akka.annotation.InternalApi
import akka.javasdk.agent.autonomous.AutonomousAgent

/**
 * INTERNAL API
 */
@InternalApi
final case class HandoffImpl(targetAgent: Class[_ <: AutonomousAgent]) extends AgentCapability
