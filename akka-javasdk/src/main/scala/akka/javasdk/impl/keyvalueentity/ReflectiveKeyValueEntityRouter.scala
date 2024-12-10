/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.keyvalueentity

import akka.annotation.InternalApi
import akka.javasdk.impl.AnySupport
import akka.javasdk.impl.CommandHandler
import akka.javasdk.impl.CommandSerialization
import akka.javasdk.impl.InvocationContext
import akka.javasdk.impl.reflection.Reflect
import akka.javasdk.impl.serialization.JsonSerializer
import akka.javasdk.keyvalueentity.CommandContext
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

  val entityStateType: Class[S] = Reflect.keyValueEntityStateType(entity.getClass).asInstanceOf[Class[S]]

  private def commandHandlerLookup(commandName: String): CommandHandler =
    commandHandlers.get(commandName) match {
      case Some(handler) => handler
      case None          => throw new HandlerNotFoundException("command", commandName, commandHandlers.keySet)
    }

  def handleCommand(
      commandName: String,
      command: BytesPayload,
      commandContext: CommandContext): KeyValueEntity.Effect[_] = {

    val commandHandler = commandHandlerLookup(commandName)

    if (serializer.isJson(command)) {
      // special cased component client calls, lets json commands through all the way
      val methodInvoker = commandHandler.getSingleNameInvoker()
      val deserializedCommand =
        CommandSerialization.deserializeComponentClientCommand(methodInvoker.method, command, serializer)
      val result = deserializedCommand match {
        case None          => methodInvoker.invoke(entity)
        case Some(command) => methodInvoker.invokeDirectly(entity, command)
      }
      result.asInstanceOf[KeyValueEntity.Effect[_]]
    } else {
      // FIXME can be proto from http-grpc-handling of the static es endpoints
      val pbAnyCommand = AnySupport.toScalaPbAny(command)
      val invocationContext =
        InvocationContext(pbAnyCommand, commandHandler.requestMessageDescriptor, commandContext.metadata())

      val inputTypeUrl = pbAnyCommand.typeUrl
      val methodInvoker = commandHandler
        .getInvoker(inputTypeUrl)

      methodInvoker
        .invoke(entity, invocationContext)
        .asInstanceOf[KeyValueEntity.Effect[_]]
    }
  }

}

/**
 * INTERNAL API
 */
@InternalApi
private[impl] final class HandlerNotFoundException(
    handlerType: String,
    val name: String,
    availableHandlers: Set[String])
    extends RuntimeException(
      s"no matching [$handlerType] handler for [$name]. " +
      s"Available handlers are: [${availableHandlers.mkString(", ")}]")
