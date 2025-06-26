/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.impl

import akka.javasdk.keyvalueentity.KeyValueEntityContext
import akka.javasdk.testkit.MockRegistry

/**
 * INTERNAL API Used by the testkit
 */
final class TestKitKeyValueEntityContext(override val entityId: String, mockRegistry: MockRegistry = MockRegistry.EMPTY)
    extends AbstractTestKitContext(mockRegistry)
    with KeyValueEntityContext {

  def this(entityId: String) = {
    this(entityId, MockRegistry.EMPTY)
  }
}
