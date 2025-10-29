/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.consumer

import java.util.Optional

import akka.annotation.InternalApi
import akka.javasdk.JsonSupport
import akka.javasdk.consumer.Consumer
import akka.javasdk.consumer.MessageContext
import akka.javasdk.consumer.MessageEnvelope
import akka.javasdk.impl.AnySupport.BytesPrimitive
import akka.javasdk.impl.AnySupport.ProtobufEmptyTypeUrl
import akka.javasdk.impl.MethodInvoker
import akka.javasdk.impl.reflection.ParameterExtractors
import akka.javasdk.impl.serialization.JsonSerializer
import akka.runtime.sdk.spi.BytesPayload

/**
 * INTERNAL API
 */
@InternalApi
private[impl] class ReflectiveConsumerRouter[A <: Consumer](
    consumer: A,
    methodInvokers: Map[String, MethodInvoker],
    internalSerializer: JsonSerializer,
    ignoreUnknown: Boolean,
    consumesFromTopic: Boolean) {

  private val serializer =
    // consuming from topic, external json format, so mapper configurable by user
    if (consumesFromTopic) new JsonSerializer(JsonSupport.getObjectMapper)
    //  non-topic is internal, so non-configurable
    else internalSerializer

  private val rawPayloadInvoker: Option[MethodInvoker] = methodInvokers.get(BytesPrimitive.fullName)

  def handleCommand(message: MessageEnvelope[BytesPayload], context: MessageContext): Consumer.Effect = {
    // only set, never cleared, to allow access from other threads in async callbacks in the consumer
    // the same handler and consumer instance is expected to only ever be invoked for a single message
    consumer._internalSetMessageContext(Optional.of(context))

    val payload = message.payload()
    // make sure we route based on the new type url if we get an old json type url message
    val inputTypeUrl = internalSerializer.removeVersion(internalSerializer.replaceLegacyJsonPrefix(payload.contentType))

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
      case None
          if rawPayloadInvoker.isDefined && inputTypeUrl != BytesPayload.EmptyContentType && inputTypeUrl != ProtobufEmptyTypeUrl =>
        // always allow consuming arbitrary payload types, except delete events, as raw bytes, if there was no method for the content type
        rawPayloadInvoker.get
          // FIXME do we need a defensive copy or can we avoid that since we don't care about the payload after this?
          .invokeDirectly(consumer, payload.bytes.toArrayUnsafe())
          .asInstanceOf[Consumer.Effect]
      case None if ignoreUnknown => ConsumerEffectImpl.Builder.ignore()
      case None =>
        inputTypeUrl match {
          case BytesPayload.EmptyContentType | ProtobufEmptyTypeUrl =>
            throw new NoSuchElementException(
              s"Couldn't find a delete handler in the Consumer [${consumer.getClass.getName}]. Please provide a parameterless handler annotated with @DeleteHandler.")
          case _ =>
            throw new NoSuchElementException(
              s"Couldn't find any method with an input type [$inputTypeUrl] in the Consumer [${consumer.getClass.getName}].")
        }
    }
  }
}
