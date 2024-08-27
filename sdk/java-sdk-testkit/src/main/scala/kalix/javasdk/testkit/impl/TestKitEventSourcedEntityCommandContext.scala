/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.testkit.impl

import akka.platform.javasdk.eventsourcedentity.CommandContext
import akka.platform.javasdk.Metadata
import akka.platform.javasdk.impl.InternalContext

/** INTERNAL API Used by the generated testkit */
final class TestKitEventSourcedEntityCommandContext(
    override val entityId: String = "stubEntityId",
    override val commandId: Long = 0L,
    override val commandName: String = "stubCommandName",
    override val sequenceNumber: Long = 0L,
    override val metadata: Metadata = Metadata.EMPTY)
    extends CommandContext
    with InternalContext {

  def this(metadata: Metadata) = {
    this(metadata = metadata, commandName = "stubCommandName")
  }

}

object TestKitEventSourcedEntityCommandContext {
  def empty = new TestKitEventSourcedEntityCommandContext()
}
