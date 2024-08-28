/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.eventsourcedentity

import akka.javasdk.eventsourcedentity.EventSourcedEntityContext
import akka.javasdk.impl.EventSourcedEntityFactory
import akka.javasdk.impl.ResolvedEntityFactory
import akka.javasdk.impl.ResolvedServiceMethod

class ResolvedEventSourcedEntityFactory(
    delegate: EventSourcedEntityFactory,
    override val resolvedMethods: Map[String, ResolvedServiceMethod[_, _]])
    extends EventSourcedEntityFactory
    with ResolvedEntityFactory {

  override def create(context: EventSourcedEntityContext): EventSourcedEntityRouter[_, _, _] =
    delegate.create(context)

}
