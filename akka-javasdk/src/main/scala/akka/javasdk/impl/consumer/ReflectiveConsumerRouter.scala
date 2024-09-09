/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.consumer

import akka.annotation.InternalApi
import akka.javasdk.consumer.Consumer
import akka.javasdk.consumer.MessageEnvelope
import akka.javasdk.impl.AnySupport.ProtobufEmptyTypeUrl
import akka.javasdk.impl.CommandHandler
import akka.javasdk.impl.InvocationContext
import akka.javasdk.impl.reflection.Reflect
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
