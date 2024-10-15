/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.timedaction

import akka.annotation.InternalApi
import akka.javasdk.impl.JsonMessageCodec
import akka.javasdk.impl.Service
import akka.javasdk.timedaction.TimedAction
import kalix.protocol.action.Actions
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * INTERNAL API
 */
@InternalApi
private[impl] class TimedActionService[A <: TimedAction](
    actionClass: Class[A],
    messageCodec: JsonMessageCodec,
    val factory: () => A)
    extends Service(actionClass, Actions.name, messageCodec) {
  lazy val log: Logger = LoggerFactory.getLogger(actionClass)

  def createRouter(): TimedActionRouter[A] =
    new ReflectiveTimedActionRouter[A](factory(), componentDescriptor.commandHandlers)
}
