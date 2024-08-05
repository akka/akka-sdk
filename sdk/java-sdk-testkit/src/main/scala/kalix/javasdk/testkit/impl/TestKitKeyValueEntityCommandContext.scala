/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.testkit.impl

import akka.platform.javasdk.Metadata
import akka.platform.javasdk.testkit.MockRegistry
import akka.platform.javasdk.keyvalueentity.{ CommandContext, KeyValueEntityContext }

/**
 * INTERNAL API Used by the generated testkit
 */
final class TestKitKeyValueEntityCommandContext(
    override val entityId: String,
    override val commandName: String = "stubCommandName",
    override val commandId: Long = 0L,
    override val metadata: Metadata = Metadata.EMPTY,
    mockRegistry: MockRegistry = MockRegistry.EMPTY)
    extends AbstractTestKitContext(mockRegistry)
    with KeyValueEntityContext
    with CommandContext {

  def this(entityId: String, metadata: Metadata) = {
    this(entityId = entityId, metadata = metadata, commandName = "stubCommandName")
  }

}