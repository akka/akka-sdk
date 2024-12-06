/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.consumer

import akka.annotation.InternalApi
import akka.javasdk.consumer.Consumer
import akka.javasdk.consumer.MessageEnvelope
import akka.javasdk.impl.AnyInvocationContext
import akka.javasdk.impl.AnySupport
import akka.javasdk.impl.AnySupport.ProtobufEmptyTypeUrl
import akka.javasdk.impl.CommandHandler
import akka.javasdk.impl.MethodInvoker
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

  private def invokerLookup(typeUrl: String): Option[MethodInvoker] = {
    commandHandlers.values
      .map(_.lookupInvoker(typeUrl))
      .collectFirst { case Some(invoker) =>
        invoker
      }
  }

  override def handleUnary(commandName: String, message: MessageEnvelope[Any]): Consumer.Effect = {

    val scalaPbAnyCommand = message.payload().asInstanceOf[ScalaPbAny]
    // make sure we route based on the new type url if we get an old json type url message
    val inputTypeUrl = AnySupport.replaceLegacyJsonPrefix(scalaPbAnyCommand.typeUrl)

    val invocationContext = new AnyInvocationContext(scalaPbAnyCommand, message.metadata())

    // lookup ComponentClient
    val componentClients = Reflect.lookupComponentClientFields(consumer)

    componentClients.foreach(_.callMetadata = Some(message.metadata()))

    val methodInvoker = invokerLookup(inputTypeUrl)
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
