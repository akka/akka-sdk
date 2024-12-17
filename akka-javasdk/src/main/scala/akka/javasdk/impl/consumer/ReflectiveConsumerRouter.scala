/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.consumer

import java.util.Optional

import akka.annotation.InternalApi
import akka.javasdk.consumer.Consumer
import akka.javasdk.consumer.MessageContext
import akka.javasdk.consumer.MessageEnvelope
import akka.javasdk.impl.AnySupport.ProtobufEmptyTypeUrl
import akka.javasdk.impl.MethodInvoker
import akka.javasdk.impl.reflection.ParameterExtractors
import akka.javasdk.impl.reflection.Reflect
import akka.javasdk.impl.serialization.JsonSerializer
import akka.runtime.sdk.spi.BytesPayload

/**
 * INTERNAL API
 */
@InternalApi
private[impl] class ReflectiveConsumerRouter[A <: Consumer](
    consumer: A,
    methodInvokers: Map[String, MethodInvoker],
    serializer: JsonSerializer,
    ignoreUnknown: Boolean) {

  def handleCommand(message: MessageEnvelope[BytesPayload], context: MessageContext): Consumer.Effect = {
    // only set, never cleared, to allow access from other threads in async callbacks in the consumer
    // the same handler and consumer instance is expected to only ever be invoked for a single message
    consumer._internalSetMessageContext(Optional.of(context))

    val payload = message.payload()
    // make sure we route based on the new type url if we get an old json type url message
    val inputTypeUrl = serializer.removeVersion(serializer.replaceLegacyJsonPrefix(payload.contentType))

    // lookup ComponentClient
    val componentClients = Reflect.lookupComponentClientFields(consumer)

    componentClients.foreach(_.callMetadata = Some(message.metadata()))

    val methodInvoker = methodInvokers.get(inputTypeUrl)
    methodInvoker match {
      case Some(invoker) =>
        inputTypeUrl match {
          case BytesPayload.EmptyContentType | ProtobufEmptyTypeUrl =>
            invoker
              .invoke(consumer)
              .asInstanceOf[Consumer.Effect]
          case _ =>
            val decodedPayload = ParameterExtractors.decodeParamPossiblySealed(
              payload,
              invoker.method.getParameterTypes.head.asInstanceOf[Class[AnyRef]],
              serializer)
            invoker
              .invokeDirectly(consumer, decodedPayload)
              .asInstanceOf[Consumer.Effect]
        }
      case None if ignoreUnknown => ConsumerEffectImpl.Builder.ignore()
      case None                  =>
        // FIXME IllegalStateException vs NoSuchElementException?
        throw new NoSuchElementException(
          s"Couldn't find any method with input type [$inputTypeUrl] in Consumer [${consumer.getClass.getName}].")
    }
  }
}
