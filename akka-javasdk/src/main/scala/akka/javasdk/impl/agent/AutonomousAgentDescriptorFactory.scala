/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import akka.annotation.InternalApi
import akka.javasdk.impl.ComponentDescriptor
import akka.javasdk.impl.ComponentDescriptorFactory
import akka.javasdk.impl.serialization.Serializer

/**
 * INTERNAL API
 *
 * Autonomous agents have no command handlers — the descriptor is empty.
 */
@InternalApi
private[impl] object AutonomousAgentDescriptorFactory extends ComponentDescriptorFactory {

  override def buildDescriptorFor(component: Class[_], serializer: Serializer): ComponentDescriptor = {
    ComponentDescriptor(Map.empty)
  }
}
