/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.keyvalueentity

import akka.annotation.InternalApi
import akka.javasdk.impl.CommandHandler
import akka.javasdk.impl.CommandSerialization
import akka.javasdk.impl.HandlerNotFoundException
import akka.javasdk.impl.serialization.JsonSerializer
import akka.javasdk.keyvalueentity.KeyValueEntity
import akka.runtime.sdk.spi.BytesPayload

/**
 * INTERNAL API
 */
@InternalApi
private[impl] class ReflectiveKeyValueEntityRouter[S, KV <: KeyValueEntity[S]](
    val entity: KV,
    commandHandlers: Map[String, CommandHandler],
    serializer: JsonSerializer) {

  private def commandHandlerLookup(commandName: String): CommandHandler =
    commandHandlers.get(commandName) match {
      case Some(handler) => handler
      case None =>
        throw new HandlerNotFoundException("command", commandName, entity.getClass, commandHandlers.keySet)
    }

  def handleCommand(commandName: String, command: BytesPayload): KeyValueEntity.Effect[_] = {

    val commandHandler = commandHandlerLookup(commandName)

    if (serializer.isJson(command) || command.isEmpty) {
      // - BytesPayload.empty - there is no real command, and we are calling a method with arity 0
      // - BytesPayload with json - we deserialize it and call the method
      val methodInvoker = commandHandler.getSingleNameInvoker()
      val deserializedCommand =
        CommandSerialization.deserializeComponentClientCommand(methodInvoker.method, command, serializer)
      val result = deserializedCommand match {
        case None          => methodInvoker.invoke(entity)
        case Some(command) => methodInvoker.invokeDirectly(entity, command)
      }
      result.asInstanceOf[KeyValueEntity.Effect[_]]
    } else {
      throw new IllegalStateException(
        s"Could not find a matching command handler for method [$commandName], content type " +
        s"[${command.contentType}], invokers keys [${commandHandler.methodInvokers.keys.mkString(", ")}," +
        s"on [${entity.getClass.getName}]")
    }
  }

}
