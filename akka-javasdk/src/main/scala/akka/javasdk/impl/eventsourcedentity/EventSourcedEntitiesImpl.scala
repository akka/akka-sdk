/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.eventsourcedentity

import akka.NotUsed
import akka.actor.ActorSystem
import akka.annotation.InternalApi
import akka.javasdk.eventsourcedentity.EventSourcedEntity
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext
import akka.javasdk.impl.Service
import akka.javasdk.impl.Settings
import akka.javasdk.impl.serialization.JsonSerializer
import akka.stream.scaladsl.Source
import io.opentelemetry.api.trace.Tracer
import kalix.protocol.event_sourced_entity._

// FIXME remove

/**
 * INTERNAL API
 */
@InternalApi
private[impl] final case class EventSourcedEntityService[S, E, ES <: EventSourcedEntity[S, E]](
    eventSourcedEntityClass: Class[_],
    _serializer: JsonSerializer,
    factory: EventSourcedEntityContext => ES,
    snapshotEvery: Int = 0)
    extends Service(eventSourcedEntityClass, EventSourcedEntities.name, _serializer) {

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
