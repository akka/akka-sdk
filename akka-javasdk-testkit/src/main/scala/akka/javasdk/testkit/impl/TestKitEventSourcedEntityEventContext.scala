/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.impl

import akka.javasdk.eventsourcedentity.EventContext

/**
 * INTERNAL API Used by the testkit
 */
final class TestKitEventSourcedEntityEventContext extends EventContext {
  override def entityId = "testkit-entity-id"
  override def sequenceNumber = 0L
  override def selfRegion: String = ""
}
