/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.impl.eventsourcedentity

import akka.platform.javasdk.impl.ResolvedEntityFactory
import akka.platform.javasdk.impl.ResolvedServiceMethod
import akka.platform.javasdk.eventsourcedentity.EventSourcedEntityContext
import akka.platform.javasdk.impl.EventSourcedEntityFactory

class ResolvedEventSourcedEntityFactory(
    delegate: EventSourcedEntityFactory,
    override val resolvedMethods: Map[String, ResolvedServiceMethod[_, _]])
    extends EventSourcedEntityFactory
    with ResolvedEntityFactory {

  override def create(context: EventSourcedEntityContext): EventSourcedEntityRouter[_, _, _] =
    delegate.create(context)

}
