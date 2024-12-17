/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.timedaction

import java.util.Optional

import akka.annotation.InternalApi
import akka.javasdk.impl.CommandHandler
import akka.javasdk.impl.CommandSerialization
import akka.javasdk.impl.HandlerNotFoundException
import akka.javasdk.impl.serialization.JsonSerializer
import akka.javasdk.timedaction.CommandContext
import akka.javasdk.timedaction.CommandEnvelope
import akka.javasdk.timedaction.TimedAction
import akka.runtime.sdk.spi.BytesPayload

/**
 * INTERNAL API
 */
@InternalApi
private[impl] final class ReflectiveTimedActionRouter[A <: TimedAction](
    action: A,
    commandHandlers: Map[String, CommandHandler],
    serializer: JsonSerializer) {

  private def commandHandlerLookup(commandName: String): CommandHandler =
    commandHandlers.get(commandName) match {
      case Some(handler) => handler
      case None =>
        throw new HandlerNotFoundException("command", commandName, action.getClass, commandHandlers.keySet)
    }

  def handleCommand(
      methodName: String,
      message: CommandEnvelope[BytesPayload],
      context: CommandContext): TimedAction.Effect = {
    // only set, never cleared, to allow access from other threads in async callbacks in the action
    // the same handler and action instance is expected to only ever be invoked for a single command
    action._internalSetCommandContext(Optional.of(context))

    val commandHandler = commandHandlerLookup(methodName)

    val payload = message.payload()

    if (serializer.isJson(payload) || payload.isEmpty) {
      // - BytesPayload.empty - there is no real command, and we are calling a method with arity 0
      // - BytesPayload with json - we deserialize it and call the method
      val methodInvoker = commandHandler.getSingleNameInvoker()
      val deserializedCommand =
        CommandSerialization.deserializeComponentClientCommand(methodInvoker.method, payload, serializer)
      val result = deserializedCommand match {
        case None          => methodInvoker.invoke(action)
        case Some(command) => methodInvoker.invokeDirectly(action, command)
      }
      result.asInstanceOf[TimedAction.Effect]
    } else {
      throw new IllegalStateException(
        s"Could not find a matching command handler for method [$methodName], content type " +
        s"[${payload.contentType}], invokers keys [${commandHandler.methodInvokers.keys.mkString(", ")}," +
        s"on [${action.getClass.getName}]")
    }
  }
}
