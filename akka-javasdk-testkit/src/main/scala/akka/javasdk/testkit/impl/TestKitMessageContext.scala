/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.impl

import java.util.Optional

import akka.javasdk.Metadata
import akka.javasdk.Tracing
import akka.javasdk.consumer.MessageContext

/**
 * INTERNAL API Used by the testkit
 */
final class TestKitMessageContext(val metadata: Metadata) extends MessageContext {

  def this() = this(Metadata.EMPTY)

  override def eventSubject(): Optional[String] =
    if (metadata.isCloudEvent)
      metadata.asCloudEvent().subject()
    else
      Optional.empty()

  override def originRegion(): Optional[String] = Optional.empty()

  override def selfRegion(): String = ""

  override def tracing(): Tracing = TestKitTracing
}
