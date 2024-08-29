/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.eventsourcedentity

import akka.annotation.InternalApi
import akka.javasdk.eventsourcedentity.EventSourcedEntityOptions

import java.util
import java.util.Collections

/**
 * INTERNAL API
 */
@InternalApi
private[impl] final case class EventSourcedEntityOptionsImpl(
    override val snapshotEvery: Int,
    override val forwardHeaders: java.util.Set[String])
    extends EventSourcedEntityOptions {

  override def withSnapshotEvery(numberOfEvents: Int): EventSourcedEntityOptions =
    copy(snapshotEvery = numberOfEvents)

  override def withForwardHeaders(headers: util.Set[String]): EventSourcedEntityOptions =
    copy(forwardHeaders = Collections.unmodifiableSet(new util.HashSet(headers)));

}
