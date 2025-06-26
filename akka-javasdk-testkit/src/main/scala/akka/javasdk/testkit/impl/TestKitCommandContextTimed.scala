/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.impl

import akka.javasdk.Metadata
import akka.javasdk.Tracing
import akka.javasdk.impl.InternalContext
import akka.javasdk.testkit.MockRegistry
import akka.javasdk.timedaction.CommandContext

/**
 * INTERNAL API Used by the testkit
 */
final class TestKitCommandContextTimed(metadata: Metadata, mockRegistry: MockRegistry = MockRegistry.EMPTY)
    extends AbstractTestKitContext(mockRegistry)
    with CommandContext
    with InternalContext {

  def this() = {
    this(Metadata.EMPTY, MockRegistry.EMPTY)
  }

  def this(metadata: Metadata) = {
    this(metadata, MockRegistry.EMPTY)
  }

  override def metadata() = metadata

  override def tracing(): Tracing = TestKitTracing
}
