/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.testkit.impl

import akka.platform.javasdk.eventsourcedentity.EventContext

/**
 * INTERNAL API Used by the generated testkit
 */
final class TestKitEventSourcedEntityEventContext extends EventContext {
  override def entityId = "testkit-entity-id"
  override def sequenceNumber = 0L
}
