/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.impl

import akka.javasdk.Metadata
import akka.javasdk.impl.InternalContext
import akka.javasdk.testkit.MockRegistry
import akka.javasdk.timedaction.CommandContext
import akka.javasdk.timedaction.TimedActionContext
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Tracer

/**
 * INTERNAL API Used by the generated testkit
 */
final class TestKitCommandContextTimed(metadata: Metadata, mockRegistry: MockRegistry = MockRegistry.EMPTY)
    extends AbstractTestKitContext(mockRegistry)
    with CommandContext
    with TimedActionContext
    with InternalContext {

  def this() = {
    this(Metadata.EMPTY, MockRegistry.EMPTY)
  }

  def this(metadata: Metadata) = {
    this(metadata, MockRegistry.EMPTY)
  }

  override def metadata() = metadata

  override def getTracer: Tracer = OpenTelemetry.noop().getTracer("noop")
}
