/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.timedaction

import akka.annotation.InternalApi
import akka.javasdk.impl.Service
import akka.javasdk.impl.serialization.JsonSerializer
import akka.javasdk.timedaction.TimedAction
import kalix.protocol.action.Actions

/**
 * INTERNAL API
 */
@InternalApi
private[impl] class TimedActionService[A <: TimedAction](actionClass: Class[A], _serializer: JsonSerializer)
    extends Service(actionClass, Actions.name, _serializer)
