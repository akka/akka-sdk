/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.consumer

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal

import akka.actor.ActorSystem
import akka.annotation.InternalApi
import akka.javasdk.consumer.Consumer
import akka.javasdk.impl.ComponentDescriptor
import akka.javasdk.impl.ErrorHandling
import akka.javasdk.impl.MetadataImpl
import akka.javasdk.impl.telemetry.Telemetry
import akka.javasdk.impl.consumer.ConsumerEffectImpl.AsyncEffect
import akka.javasdk.impl.consumer.ConsumerEffectImpl.IgnoreEffect
import akka.javasdk.impl.consumer.ConsumerEffectImpl.ReplyEffect
import akka.javasdk.consumer.MessageContext
import akka.javasdk.consumer.MessageEnvelope
import akka.javasdk.impl.AnySupport
import akka.javasdk.impl.serialization.JsonSerializer
import akka.runtime.sdk.spi.SpiConsumer
import akka.runtime.sdk.spi.SpiConsumer.Message
import akka.runtime.sdk.spi.SpiConsumer.Effect
import akka.runtime.sdk.spi.SpiMetadata
import akka.runtime.sdk.spi.TimerClient
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC

/** EndMarker */
@InternalApi
private[impl] final class ConsumerImpl[C <: Consumer](
    val factory: () => C,
    consumerClass: Class[C],
    _system: ActorSystem,
    timerClient: TimerClient,
    sdkExecutionContext: ExecutionContext,
    tracerFactory: () => Tracer,
    serializer: JsonSerializer,
    ignoreUnknown: Boolean)
    extends SpiConsumer {

  private val log: Logger = LoggerFactory.getLogger(consumerClass)

  private implicit val executionContext: ExecutionContext = sdkExecutionContext
  implicit val system: ActorSystem = _system

  //FIXME remove ComponentDescriptor and just get command handler/method invokers
  private val componentDescriptor = ComponentDescriptor.descriptorFor(consumerClass, serializer)

  // FIXME remove router altogether
  private def createRouter(): ReflectiveConsumerRouter[C] =
    new ReflectiveConsumerRouter[C](factory(), componentDescriptor.commandHandlers, ignoreUnknown)

  override def handleMessage(message: Message): Future[Effect] = {
    val span: Option[Span] = None //FIXME add intrumentation

    span.foreach(s => MDC.put(Telemetry.TRACE_ID, s.getSpanContext.getTraceId))
    val fut =
      try {
        val messageContext = createMessageContext(message, span)
        val pbAnyPayload =
          AnySupport.toScalaPbAny(message.payload.getOrElse(throw new IllegalArgumentException("No message payload")))
        // FIXME shall we deserialize here or in the router? the router needs the contentType as well.
//        val decodedPayload =
//          serializer.fromBytes(message.payload.getOrElse(throw new IllegalArgumentException("No message payload")))
        val effect = createRouter()
          .handleUnary(message.name, MessageEnvelope.of(pbAnyPayload, messageContext.metadata()), messageContext)
        toSpiEffect(message, effect)
      } catch {
        case NonFatal(ex) =>
          // command handler threw an "unexpected" error
          span.foreach(_.end())
          Future.successful(handleUnexpectedException(message, ex))
      } finally {
        MDC.remove(Telemetry.TRACE_ID)
      }
    fut.andThen { case _ =>
      span.foreach(_.end())
    }
  }

  private def createMessageContext(message: Message, span: Option[Span]): MessageContext = {
    val metadata = MetadataImpl.of(message.metadata)
    val updatedMetadata = span.map(metadata.withTracing).getOrElse(metadata)
    new MessageContextImpl(updatedMetadata, timerClient, tracerFactory, span)
  }

  private def toSpiEffect(message: Message, effect: Consumer.Effect): Future[Effect] = {
    effect match {
      case ReplyEffect(msg, metadata) =>
        Future.successful(
          new Effect(
            ignore = false,
            reply = Some(serializer.toBytes(msg)),
            metadata = MetadataImpl.toSpi(metadata),
            error = None))
      case AsyncEffect(futureEffect) =>
        futureEffect
          .flatMap { effect => toSpiEffect(message, effect) }
          .recover { case NonFatal(ex) =>
            handleUnexpectedException(message, ex)
          }
      case IgnoreEffect =>
        Future.successful(new Effect(ignore = true, reply = None, metadata = SpiMetadata.Empty, error = None))
      case unknown =>
        throw new IllegalArgumentException(s"Unknown TimedAction.Effect type ${unknown.getClass}")
    }
  }

  private def handleUnexpectedException(message: Message, ex: Throwable): Effect = {
    ex match {
      case _ =>
        ErrorHandling.withCorrelationId { correlationId =>
          log.error(
            s"Failure during handling message [${message.name}] from Consumer component [${consumerClass.getSimpleName}].",
            ex)
          protocolFailure(correlationId)
        }
    }
  }

  private def protocolFailure(correlationId: String): Effect = {
    new Effect(
      ignore = false,
      reply = None,
      metadata = SpiMetadata.Empty,
      error = Some(new SpiConsumer.Error(s"Unexpected error [$correlationId]")))
  }

}
