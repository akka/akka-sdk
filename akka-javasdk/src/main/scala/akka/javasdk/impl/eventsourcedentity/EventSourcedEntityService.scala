/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.eventsourcedentity

import akka.annotation.InternalApi
import akka.javasdk.eventsourcedentity.EventSourcedEntity
import akka.javasdk.impl.Service
import akka.javasdk.impl.serialization.JsonSerializer
import kalix.protocol.event_sourced_entity._

// FIXME remove

/**
 * INTERNAL API
 */
@InternalApi
private[impl] final case class EventSourcedEntityService[S, E, ES <: EventSourcedEntity[S, E]](
    eventSourcedEntityClass: Class[_],
    _serializer: JsonSerializer)
    extends Service(eventSourcedEntityClass, EventSourcedEntities.name, _serializer) {}
