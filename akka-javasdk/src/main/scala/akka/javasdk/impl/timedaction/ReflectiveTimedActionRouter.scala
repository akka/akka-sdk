/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.timedaction

import akka.annotation.InternalApi
import akka.javasdk.impl.AnySupport
import akka.javasdk.impl.CommandHandler
import akka.javasdk.impl.CommandSerialization
import akka.javasdk.impl.serialization.JsonSerializer
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
    serializer: JsonSerializer)
    extends TimedActionRouter[A](action) {

  private def commandHandlerLookup(methodName: String) =
    commandHandlers.getOrElse(
      methodName,
      throw new RuntimeException(
        s"no matching method for '$methodName' on [${action.getClass}], existing are [${commandHandlers.keySet
          .mkString(", ")}]"))

  override def handleUnary(methodName: String, message: CommandEnvelope[BytesPayload]): TimedAction.Effect = {

    val commandHandler = commandHandlerLookup(methodName)

    val payload = message.payload()
    // make sure we route based on the new type url if we get an old json type url message
    val updatedContentType = AnySupport.replaceLegacyJsonPrefix(payload.contentType)
    if ((AnySupport.isJson(updatedContentType) || payload.bytes.isEmpty) && commandHandler.isSingleNameInvoker) {
      // special cased component client calls, lets json commands trough all the way
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
        "Could not find a matching command handler for command: " + methodName + ", content type: " + updatedContentType + ", invokers keys: " + commandHandler.methodInvokers.keys
          .mkString(", "))
    }
  }
}
