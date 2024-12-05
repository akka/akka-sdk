/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.eventsourcedentity

import akka.NotUsed
import akka.actor.ActorSystem
import akka.annotation.InternalApi
import akka.javasdk.eventsourcedentity.EventSourcedEntity
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext
import akka.javasdk.impl.JsonMessageCodec
import akka.javasdk.impl.Service
import akka.javasdk.impl.Settings
import akka.stream.scaladsl.Source
import io.opentelemetry.api.trace.Tracer
import kalix.protocol.event_sourced_entity._

/**
 * INTERNAL API
 */
@InternalApi
private[impl] final case class EventSourcedEntityService[S, E, ES <: EventSourcedEntity[S, E]](
    eventSourcedEntityClass: Class[_],
    _messageCodec: JsonMessageCodec,
    factory: EventSourcedEntityContext => ES,
    snapshotEvery: Int = 0)
    extends Service(eventSourcedEntityClass, EventSourcedEntities.name, _messageCodec) {

  def createRouter(context: EventSourcedEntityContext) = ???
}

/**
 * INTERNAL API
 */
@InternalApi
private[impl] final class EventSourcedEntitiesImpl(
    system: ActorSystem,
    _services: Map[String, EventSourcedEntityService[_, _, _]],
    configuration: Settings,
    sdkDispatcherName: String,
    tracerFactory: () => Tracer)
    extends EventSourcedEntities {

  override def handle(in: Source[EventSourcedStreamIn, NotUsed]): Source[EventSourcedStreamOut, NotUsed] = ???
}
