/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.testkit.impl

import akka.platform.javasdk.keyvalueentity.KeyValueEntityContext
import akka.platform.javasdk.testkit.MockRegistry

/**
 * INTERNAL API Used by the generated testkit
 */
final class TestKitKeyValueEntityContext(override val entityId: String, mockRegistry: MockRegistry = MockRegistry.EMPTY)
    extends AbstractTestKitContext(mockRegistry)
    with KeyValueEntityContext {

  def this(entityId: String) = {
    this(entityId, MockRegistry.EMPTY)
  }
}
