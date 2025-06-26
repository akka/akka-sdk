/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.keyvalueentity

import akka.annotation.InternalApi
import akka.javasdk.impl.MethodInvoker
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
    methodInvokers: Map[String, MethodInvoker],
    serializer: JsonSerializer) {

  private def methodInvokerLookup(commandName: String): MethodInvoker =
    methodInvokers.get(commandName) match {
      case Some(handler) => handler
      case None =>
        throw new HandlerNotFoundException("command", commandName, entity.getClass, methodInvokers.keySet)
    }

  def handleCommand(commandName: String, command: BytesPayload): KeyValueEntity.Effect[_] = {

    val methodInvoker = methodInvokerLookup(commandName)

    if (serializer.isJson(command) || command.isEmpty) {
      // - BytesPayload.empty - there is no real command, and we are calling a method with arity 0
      // - BytesPayload with json - we deserialize it and call the method
      val deserializedCommand =
        CommandSerialization.deserializeComponentClientCommand(methodInvoker.method, command, serializer)
      val result = deserializedCommand match {
        case None          => methodInvoker.invoke(entity)
        case Some(command) => methodInvoker.invokeDirectly(entity, command)
      }
      result.asInstanceOf[KeyValueEntity.Effect[_]]
    } else {
      throw new IllegalStateException(
        s"Could not find a matching command handler for method [$commandName], content type [${command.contentType}] " +
        s"on [${entity.getClass.getName}]")
    }
  }

}
