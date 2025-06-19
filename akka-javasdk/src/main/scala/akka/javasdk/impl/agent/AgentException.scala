/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import akka.annotation.InternalApi

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] object AgentException {
  def apply(message: String, cause: Option[Throwable]): AgentException =
    AgentException(commandName = "", message, cause)
}

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] final case class AgentException(commandName: String, message: String, cause: Option[Throwable])
    extends RuntimeException(message, cause.orNull) {}
