/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.impl

import akka.javasdk.Metadata
import akka.javasdk.Tracing
import akka.javasdk.eventsourcedentity.CommandContext
import akka.javasdk.impl.InternalContext

/** INTERNAL API Used by the generated testkit */
final class TestKitEventSourcedEntityCommandContext(
    override val entityId: String = "stubEntityId",
    override val commandId: Long = 0L,
    override val commandName: String = "stubCommandName",
    override val sequenceNumber: Long = 0L,
    override val metadata: Metadata = Metadata.EMPTY)
    extends CommandContext
    with InternalContext {

  def this(entityId: String, metadata: Metadata) = {
    this(metadata = metadata, commandName = "stubCommandName", entityId = entityId)
  }

  override def tracing(): Tracing = TestKitTracing

}

object TestKitEventSourcedEntityCommandContext {
  def empty = new TestKitEventSourcedEntityCommandContext()
}
