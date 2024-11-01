/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.timedaction

import akka.annotation.InternalApi
import akka.javasdk.JsonSupport
import akka.javasdk.impl.CommandHandler
import akka.javasdk.impl.InvocationContext
import akka.javasdk.impl.reflection.Reflect
import akka.javasdk.impl.AnySupport.ProtobufEmptyTypeUrl
import akka.javasdk.impl.CommandSerialization
import akka.javasdk.timedaction.CommandEnvelope
import akka.javasdk.timedaction.TimedAction
import com.google.protobuf.any.{ Any => ScalaPbAny }

/**
 * INTERNAL API
 */
@InternalApi
private[impl] final class ReflectiveTimedActionRouter[A <: TimedAction](
    action: A,
    commandHandlers: Map[String, CommandHandler])
    extends TimedActionRouter[A](action) {

  private def commandHandlerLookup(commandName: String) =
    commandHandlers.getOrElse(
      commandName,
      throw new RuntimeException(
        s"no matching method for '$commandName' on [${action.getClass}], existing are [${commandHandlers.keySet
          .mkString(", ")}]"))

  override def handleUnary(commandName: String, message: CommandEnvelope[Any]): TimedAction.Effect = {

    val commandHandler = commandHandlerLookup(commandName)

    val inputTypeUrl = message.payload().asInstanceOf[ScalaPbAny].typeUrl
    val scalaPbAnyCommand = message.payload().asInstanceOf[ScalaPbAny]
    if ((scalaPbAnyCommand.typeUrl.startsWith(
        JsonSupport.JSON_TYPE_URL_PREFIX) || scalaPbAnyCommand.value.isEmpty) && commandHandler.isSingleNameInvoker) {
      // special cased component client calls, lets json commands trough all the way
      val methodInvoker = commandHandler.getSingleNameInvoker()
      val deserializedCommand =
        CommandSerialization.deserializeComponentClientCommand(methodInvoker.method, scalaPbAnyCommand)
      val result = deserializedCommand match {
        case None          => methodInvoker.invoke(action)
        case Some(command) => methodInvoker.invokeDirectly(action, command)
      }
      result.asInstanceOf[TimedAction.Effect]
    } else {

      val invocationContext =
        InvocationContext(scalaPbAnyCommand, commandHandler.requestMessageDescriptor, message.metadata())

      // lookup ComponentClient
      val componentClients = Reflect.lookupComponentClientFields(action)

      // inject call metadata
      componentClients.foreach(cc =>
        cc.callMetadata =
          cc.callMetadata.map(existing => existing.merge(message.metadata())).orElse(Some(message.metadata())))

      val methodInvoker = commandHandler.lookupInvoker(inputTypeUrl)
      methodInvoker match {
        case Some(invoker) =>
          inputTypeUrl match {
            case ProtobufEmptyTypeUrl =>
              invoker
                .invoke(action)
                .asInstanceOf[TimedAction.Effect]
            case _ =>
              invoker
                .invoke(action, invocationContext)
                .asInstanceOf[TimedAction.Effect]
          }
        case None =>
          throw new NoSuchElementException(
            s"Couldn't find any method with input type [$inputTypeUrl] in Action [$action].")
      }
    }
  }
}
