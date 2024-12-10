/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.annotation.InternalApi
import akka.javasdk.eventsourcedentity.CommandContext
import akka.javasdk.keyvalueentity
import kalix.protocol.entity.Command
import kalix.protocol.event_sourced_entity.EventSourcedInit
import kalix.protocol.value_entity.ValueEntityInit

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] object EntityExceptions {

  final case class EntityException(entityId: String, commandName: String, message: String, cause: Option[Throwable])
      extends RuntimeException(message, cause.orNull) {
    def this(entityId: String, commandName: String, message: String) =
      this(entityId, commandName, message, None)
  }

  object EntityException {
    def apply(message: String): EntityException =
      EntityException(entityId = "", commandName = "", message, None)

    def apply(message: String, cause: Option[Throwable]): EntityException =
      EntityException(entityId = "", commandName = "", message, cause)

    def apply(command: Command, message: String): EntityException =
      EntityException(command.entityId, command.name, message, None)

    def apply(command: Command, message: String, cause: Option[Throwable]): EntityException =
      EntityException(command.entityId, command.name, message, cause)

    def apply(context: keyvalueentity.CommandContext, message: String): EntityException =
      EntityException(context.entityId, context.commandName, message, None)

    def apply(context: keyvalueentity.CommandContext, message: String, cause: Option[Throwable]): EntityException =
      EntityException(context.entityId, context.commandName, message, cause)

    def apply(context: CommandContext, message: String): EntityException =
      EntityException(context.entityId, context.commandName, message, None)

    def apply(context: CommandContext, message: String, cause: Option[Throwable]): EntityException =
      EntityException(context.entityId, context.commandName, message, cause)
  }

  object ProtocolException {
    def apply(message: String): EntityException =
      EntityException(entityId = "", commandName = "", "Protocol error: " + message, None)

    def apply(command: Command, message: String): EntityException =
      EntityException(command.entityId, command.name, "Protocol error: " + message, None)

    def apply(entityId: String, message: String): EntityException =
      EntityException(entityId, commandName = "", "Protocol error: " + message, None)

    def apply(init: ValueEntityInit, message: String): EntityException =
      ProtocolException(init.entityId, message)

    def apply(init: EventSourcedInit, message: String): EntityException =
      ProtocolException(init.entityId, message)

  }

}
