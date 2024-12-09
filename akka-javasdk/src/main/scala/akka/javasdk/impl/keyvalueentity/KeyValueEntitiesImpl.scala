/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.keyvalueentity

import akka.NotUsed
import akka.actor.ActorSystem
import akka.annotation.InternalApi
import akka.javasdk.impl.Service
import akka.javasdk.impl.Settings
import akka.javasdk.impl.serialization.JsonSerializer
import akka.javasdk.keyvalueentity.KeyValueEntity
import akka.javasdk.keyvalueentity.KeyValueEntityContext
import akka.stream.scaladsl.Source
import io.opentelemetry.api.trace.Tracer
import kalix.protocol.value_entity._

// FIXME remove

/**
 * INTERNAL API
 */
@InternalApi
private[impl] final class KeyValueEntityService[S, E <: KeyValueEntity[S]](
    entityClass: Class[E],
    serializer: JsonSerializer,
    factory: KeyValueEntityContext => E)
    extends Service(entityClass, ValueEntities.name, serializer) {
  def createRouter(context: KeyValueEntityContext) = ???
}

/**
 * INTERNAL API
 */
@InternalApi
private[impl] final class KeyValueEntitiesImpl(
    system: ActorSystem,
    val services: Map[String, KeyValueEntityService[_, _]],
    configuration: Settings,
    sdkDispatcherName: String,
    tracerFactory: () => Tracer)
    extends ValueEntities {

  override def handle(in: Source[ValueEntityStreamIn, NotUsed]): Source[ValueEntityStreamOut, NotUsed] = ???
}
