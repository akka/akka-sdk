/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.testkit.impl

import akka.platform.javasdk.testkit.MockRegistry
import akka.stream.Materializer
import akka.platform.javasdk.valueentity.ValueEntityContext

/**
 * INTERNAL API Used by the generated testkit
 */
final class TestKitValueEntityContext(override val entityId: String, mockRegistry: MockRegistry = MockRegistry.EMPTY)
    extends AbstractTestKitContext(mockRegistry)
    with ValueEntityContext {

  def this(entityId: String) = {
    this(entityId, MockRegistry.EMPTY)
  }

  override def materializer(): Materializer = throw new UnsupportedOperationException(
    "Accessing the materializer from testkit not supported yet")
}
