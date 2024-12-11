/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.consumer

import akka.annotation.InternalApi
import akka.javasdk.consumer.Consumer
import akka.javasdk.consumer.MessageEnvelope
import akka.javasdk.impl.AnySupport
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
    ignoreUnknown: Boolean)
    extends ConsumerRouter[A](consumer) {

  override def handleUnary(message: MessageEnvelope[BytesPayload]): Consumer.Effect = {

    val payload = message.payload()
    // make sure we route based on the new type url if we get an old json type url message
    val inputTypeUrl = serializer.removeVersion(AnySupport.replaceLegacyJsonPrefix(payload.contentType))

    // lookup ComponentClient
    val componentClients = Reflect.lookupComponentClientFields(consumer)

    componentClients.foreach(_.callMetadata = Some(message.metadata()))

    val methodInvoker = methodInvokers.get(inputTypeUrl)
    methodInvoker match {
      case Some(invoker) =>
        inputTypeUrl match {
          case ProtobufEmptyTypeUrl =>
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
      case None =>
        throw new NoSuchElementException(
          s"Couldn't find any method with input type [$inputTypeUrl] in Consumer [$consumer].")
    }
  }
}
