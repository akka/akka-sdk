/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package kalix.javasdk.impl.eventsourcedentity

import kalix.javasdk.impl.ResolvedEntityFactory
import kalix.javasdk.impl.ResolvedServiceMethod
import kalix.javasdk.eventsourcedentity.EventSourcedEntityContext
import kalix.javasdk.impl.EventSourcedEntityFactory

class ResolvedEventSourcedEntityFactory(
    delegate: EventSourcedEntityFactory,
    override val resolvedMethods: Map[String, ResolvedServiceMethod[_, _]])
    extends EventSourcedEntityFactory
    with ResolvedEntityFactory {

  override def create(context: EventSourcedEntityContext): EventSourcedEntityRouter[_, _, _] =
    delegate.create(context)

}
