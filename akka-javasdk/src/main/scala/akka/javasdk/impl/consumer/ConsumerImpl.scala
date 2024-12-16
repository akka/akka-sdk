/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.consumer

import java.util.Optional

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal

import akka.actor.ActorSystem
import akka.annotation.InternalApi
import akka.javasdk.Metadata
import akka.javasdk.Tracing
import akka.javasdk.consumer.Consumer
import akka.javasdk.consumer.MessageContext
import akka.javasdk.consumer.MessageEnvelope
import akka.javasdk.impl.AbstractContext
import akka.javasdk.impl.ComponentDescriptor
import akka.javasdk.impl.ErrorHandling
import akka.javasdk.impl.MetadataImpl
import akka.javasdk.impl.consumer.ConsumerEffectImpl.AsyncEffect
import akka.javasdk.impl.consumer.ConsumerEffectImpl.IgnoreEffect
import akka.javasdk.impl.consumer.ConsumerEffectImpl.ProduceEffect
import akka.javasdk.impl.serialization.JsonSerializer
import akka.javasdk.impl.telemetry.SpanTracingImpl
import akka.javasdk.impl.telemetry.Telemetry
import akka.javasdk.impl.timer.TimerSchedulerImpl
import akka.javasdk.timer.TimerScheduler
import akka.runtime.sdk.spi.BytesPayload
import akka.runtime.sdk.spi.SpiConsumer
import akka.runtime.sdk.spi.SpiConsumer.Effect
import akka.runtime.sdk.spi.SpiConsumer.Message
import akka.runtime.sdk.spi.SpiMetadataEntry
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
    ignoreUnknown: Boolean,
    componentDescriptor: ComponentDescriptor)
    extends SpiConsumer {

  private val log: Logger = LoggerFactory.getLogger(consumerClass)

  private implicit val executionContext: ExecutionContext = sdkExecutionContext
  implicit val system: ActorSystem = _system

  // FIXME remove router altogether
  private def createRouter(): ReflectiveConsumerRouter[C] =
    new ReflectiveConsumerRouter[C](
      factory(),
      componentDescriptor.commandHandlers.values.head.methodInvokers,
      serializer,
      ignoreUnknown)

  override def handleMessage(message: Message): Future[Effect] = {
    val span: Option[Span] = None //FIXME add intrumentation

    span.foreach(s => MDC.put(Telemetry.TRACE_ID, s.getSpanContext.getTraceId))
    val fut =
      try {
        val messageContext = createMessageContext(message, span)
        val payload: BytesPayload = message.payload.getOrElse(throw new IllegalArgumentException("No message payload"))
        val effect = createRouter()
          .handleUnary(MessageEnvelope.of(payload, messageContext.metadata()), messageContext)
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
      case ProduceEffect(msg, metadata) =>
        Future.successful(
          new SpiConsumer.ProduceEffect(
            payload = Some(serializer.toBytes(msg)),
            metadata = MetadataImpl.toSpi(metadata)))
      case AsyncEffect(futureEffect) =>
        futureEffect
          .flatMap { effect => toSpiEffect(message, effect) }
          .recover { case NonFatal(ex) =>
            handleUnexpectedException(message, ex)
          }
      case IgnoreEffect =>
        Future.successful(new SpiConsumer.IgnoreEffect)
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
    new SpiConsumer.ErrorEffect(error = new SpiConsumer.Error(s"Unexpected error [$correlationId]"))
  }

}

/**
 * INTERNAL API
 */
@InternalApi
private[impl] final case class MessageEnvelopeImpl[T](payload: T, metadata: Metadata) extends MessageEnvelope[T]

/**
 * INTERNAL API
 */
@InternalApi
private[impl] final class MessageContextImpl(
    override val metadata: Metadata,
    timerClient: TimerClient,
    tracerFactory: () => Tracer,
    span: Option[Span])
    extends AbstractContext
    with MessageContext {

  val timers: TimerScheduler = new TimerSchedulerImpl(timerClient, componentCallMetadata)

  override def eventSubject(): Optional[String] =
    if (metadata.isCloudEvent)
      metadata.asCloudEvent().subject()
    else
      Optional.empty()

  override def componentCallMetadata: MetadataImpl = {
    if (metadata.has(Telemetry.TRACE_PARENT_KEY)) {
      MetadataImpl.of(
        List(new SpiMetadataEntry(Telemetry.TRACE_PARENT_KEY, metadata.get(Telemetry.TRACE_PARENT_KEY).get())))
    } else {
      MetadataImpl.Empty
    }
  }

  override def tracing(): Tracing = new SpanTracingImpl(span, tracerFactory)
}
