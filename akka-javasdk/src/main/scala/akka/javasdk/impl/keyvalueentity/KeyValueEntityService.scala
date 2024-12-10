/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.keyvalueentity

import akka.annotation.InternalApi
import akka.javasdk.impl.Service
import akka.javasdk.impl.serialization.JsonSerializer
import akka.javasdk.keyvalueentity.KeyValueEntity
import kalix.protocol.value_entity._

// FIXME remove

/**
 * INTERNAL API
 */
@InternalApi
private[impl] final class KeyValueEntityService[S, E <: KeyValueEntity[S]](
    entityClass: Class[E],
    serializer: JsonSerializer)
    extends Service(entityClass, ValueEntities.name, serializer)
