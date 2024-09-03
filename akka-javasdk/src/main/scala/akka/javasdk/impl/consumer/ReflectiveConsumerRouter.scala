/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.consumer

import scala.util.control.NonFatal

import akka.annotation.InternalApi
import akka.javasdk.JsonSupport
import akka.javasdk.consumer.Consumer
import akka.javasdk.consumer.MessageEnvelope
import akka.javasdk.impl.CommandHandler
import akka.javasdk.impl.InvocationContext
import akka.javasdk.impl.reflection.Reflect
import akka.javasdk.impl.AnySupport.ProtobufEmptyTypeUrl
import com.google.protobuf.any.{ Any => ScalaPbAny }

/**
 * INTERNAL API
 */
@InternalApi
private[impl] class ReflectiveConsumerRouter[A <: Consumer](
    consumer: A,
    commandHandlers: Map[String, CommandHandler],
    ignoreUnknown: Boolean)
    extends ConsumerRouter[A](consumer) {

  private def commandHandlerLookup(commandName: String) =
    commandHandlers.getOrElse(
      commandName,
      throw new RuntimeException(
        s"no matching method for '$commandName' on [${consumer.getClass}], existing are [${commandHandlers.keySet
          .mkString(", ")}]"))

  override def handleUnary(commandName: String, message: MessageEnvelope[Any]): Consumer.Effect = {

    val commandHandler = commandHandlerLookup(commandName)

    val inputTypeUrl = message.payload().asInstanceOf[ScalaPbAny].typeUrl
    val scalaPbAnyCommand = message.payload().asInstanceOf[ScalaPbAny]
    if ((scalaPbAnyCommand.typeUrl.startsWith(
        JsonSupport.JSON_TYPE_URL_PREFIX) || scalaPbAnyCommand.value.isEmpty) && commandHandler.isSingleNameInvoker) {
      // special cased component client calls, lets json commands trough all the way
      val methodInvoker = commandHandler.getSingleNameInvoker()
      val parameterTypes = methodInvoker.method.getParameterTypes
      val result =
        if (parameterTypes.isEmpty) methodInvoker.invoke(consumer)
        else if (parameterTypes.size > 1)
          throw new IllegalArgumentException(
            s"Handler for [${consumer.getClass}.$commandName] expects more than one parameter, not supported (parameter types: [${parameterTypes.mkString}]")
        else {
          // we used to dispatch based on the type, since that is how it works in protobuf for eventing
          // but here we have a concrete command name, and can pick up the expected serialized type from there
          val decodedParameter =
            try {
              JsonSupport.decodeJson(parameterTypes(0), scalaPbAnyCommand)
            } catch {
              case NonFatal(ex) =>
                throw new IllegalArgumentException(
                  s"Could not deserialize message for ${consumer.getClass}.${commandName}",
                  ex)
            }
          methodInvoker.invokeDirectly(consumer, decodedParameter.asInstanceOf[AnyRef])
        }
      result.asInstanceOf[Consumer.Effect]
    } else {

      val invocationContext =
        InvocationContext(scalaPbAnyCommand, commandHandler.requestMessageDescriptor, message.metadata())

      // lookup ComponentClient
      val componentClients = Reflect.lookupComponentClientFields(consumer)

      componentClients.foreach(_.callMetadata = Some(message.metadata()))

      val methodInvoker = commandHandler.lookupInvoker(inputTypeUrl)
      methodInvoker match {
        case Some(invoker) =>
          inputTypeUrl match {
            case ProtobufEmptyTypeUrl =>
              invoker
                .invoke(consumer)
                .asInstanceOf[Consumer.Effect]
            case _ =>
              invoker
                .invoke(consumer, invocationContext)
                .asInstanceOf[Consumer.Effect]
          }
        case None if ignoreUnknown => ConsumerEffectImpl.Builder.ignore()
        case None =>
          throw new NoSuchElementException(
            s"Couldn't find any method with input type [$inputTypeUrl] in Consumer [$consumer].")
      }
    }
  }
}
