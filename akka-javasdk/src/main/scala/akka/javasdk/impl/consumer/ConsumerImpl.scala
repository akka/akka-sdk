/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.consumer

import java.util.Optional

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.jdk.OptionConverters.RichOption
import scala.util.control.NonFatal

import akka.actor.ActorSystem
import akka.annotation.InternalApi
import akka.javasdk.JsonSupport
import akka.javasdk.Metadata
import akka.javasdk.Tracing
import akka.javasdk.consumer.Consumer
import akka.javasdk.consumer.MessageContext
import akka.javasdk.consumer.MessageEnvelope
import akka.javasdk.impl.AbstractContext
import akka.javasdk.impl.AnySupport
import akka.javasdk.impl.ComponentDescriptor
import akka.javasdk.impl.ErrorHandling
import akka.javasdk.impl.MetadataImpl
import akka.javasdk.impl.consumer.ConsumerEffectImpl.AsyncEffect
import akka.javasdk.impl.consumer.ConsumerEffectImpl.ConsumedEffect
import akka.javasdk.impl.consumer.ConsumerEffectImpl.ProduceEffect
import akka.javasdk.impl.serialization.JsonSerializer
import akka.javasdk.impl.telemetry.SpanTracingImpl
import akka.javasdk.impl.telemetry.Telemetry
import akka.javasdk.impl.timer.TimerSchedulerImpl
import akka.javasdk.timer.TimerScheduler
import akka.runtime.sdk.spi.BytesPayload
import akka.runtime.sdk.spi.ConsumerDestination
import akka.runtime.sdk.spi.ConsumerDestination.TopicDestination
import akka.runtime.sdk.spi.ConsumerSource
import akka.runtime.sdk.spi.ConsumerSource.TopicSource
import akka.runtime.sdk.spi.RegionInfo
import akka.runtime.sdk.spi.SpiConsumer
import akka.runtime.sdk.spi.SpiConsumer.Effect
import akka.runtime.sdk.spi.SpiConsumer.Message
import akka.runtime.sdk.spi.TimerClient
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.{ Context => OtelContext }
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC

@InternalApi
private[impl] final class ConsumerImpl[C <: Consumer](
    componentId: String,
    val factory: MessageContext => C,
    consumerClass: Class[C],
    consumerSource: ConsumerSource,
    consumerDestination: Option[ConsumerDestination],
    _system: ActorSystem,
    timerClient: TimerClient,
    sdkExecutionContext: ExecutionContext,
    tracerFactory: () => Tracer,
    internalSerializer: JsonSerializer,
    ignoreUnknown: Boolean,
    componentDescriptor: ComponentDescriptor,
    regionInfo: RegionInfo)
    extends SpiConsumer {

  private val log: Logger = LoggerFactory.getLogger(consumerClass)

  private implicit val executionContext: ExecutionContext = sdkExecutionContext
  implicit val system: ActorSystem = _system

  private val resultSerializer =
    // producing to topic, external json format, so mapper configurable by user
    if (consumerDestination.exists(_.isInstanceOf[TopicDestination])) new JsonSerializer(JsonSupport.getObjectMapper)
    // non-topic is internal, so non-configurable (also means no output json is ever passed anywhere though)
    else internalSerializer

  private def createRouter(consumer: C): ReflectiveConsumerRouter[C] =
    new ReflectiveConsumerRouter[C](
      consumer,
      componentDescriptor.methodInvokers,
      internalSerializer,
      ignoreUnknown,
      consumesFromTopic = consumerSource.isInstanceOf[TopicSource])

  override def handleMessage(message: Message): Future[Effect] = {
    val metadata = {
      val asIs = MetadataImpl.of(message.metadata)
      message.payload match {
        case Some(payload) =>
          // or else we get 'application/vnd.kalix.protobuf.any' from the runtime
          // FIXME should maybe be sorted in the runtime which unwraps the payload?
          asIs.set(MetadataImpl.CeDatacontenttype, AnySupport.typeUrlToContentType(payload.contentType))
        case None => asIs
      }
    }
    val telemetryContext = Option(message.telemetryContext)
    val traceId = telemetryContext.flatMap { context =>
      Option(Span.fromContextOrNull(context)).map(_.getSpanContext.getTraceId)
    }

    traceId.foreach(id => MDC.put(Telemetry.TRACE_ID, id))
    try {
      val messageContext =
        new MessageContextImpl(
          metadata,
          timerClient,
          tracerFactory,
          telemetryContext,
          regionInfo.selfRegion,
          message.originRegion.toJava)

      val consumer = factory(messageContext)

      val payload: BytesPayload = message.payload.getOrElse(throw new IllegalArgumentException("No message payload"))
      val effect = createRouter(consumer)
        .handleCommand(MessageEnvelope.of(payload, messageContext.metadata), messageContext)
      toSpiEffect(message, effect)
    } catch {
      case NonFatal(ex) =>
        // command handler threw an "unexpected" error, also covers HandlerNotFoundException
        Future.successful(handleUnexpectedException(message, ex))
    } finally {
      if (traceId.isDefined) MDC.remove(Telemetry.TRACE_ID)
    }
  }

  private def toSpiEffect(message: Message, effect: Consumer.Effect): Future[Effect] = {
    effect match {
      case ConsumedEffect => Future.successful(SpiConsumer.ConsumedEffect)
      case ProduceEffect(msg, metadata) =>
        if (consumerDestination.isEmpty) {
          val baseMsg = s"Consumer [$componentId] produced a message but no destination is defined."
          log.error(baseMsg + " Add @Produce annotation or change the Consumer.Effect outcome.")
          Future.successful(new SpiConsumer.ErrorEffect(new SpiConsumer.Error(baseMsg)))
        } else {
          Future.successful(
            new SpiConsumer.ProduceEffect(
              payload = Some(resultSerializer.toBytes(msg)),
              metadata = MetadataImpl.toSpi(metadata)))
        }
      case AsyncEffect(futureEffect) =>
        futureEffect
          .flatMap { effect => toSpiEffect(message, effect) }
          .recover { case NonFatal(ex) =>
            handleUnexpectedException(message, ex)
          }
      case unknown =>
        throw new IllegalArgumentException(s"Unknown TimedAction.Effect type ${unknown.getClass}")
    }
  }

  private def handleUnexpectedException(message: Message, ex: Throwable): Effect = {
    ErrorHandling.withCorrelationId { correlationId =>
      log.error(
        s"Failure during handling message of type [${message.payload.fold("none")(
          _.contentType)}] from Consumer component [${consumerClass.getSimpleName}].",
        ex)
      protocolFailure(correlationId)
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
    val telemetryContext: Option[OtelContext],
    override val selfRegion: String,
    override val originRegion: Optional[String])
    extends AbstractContext
    with MessageContext {

  val timers: TimerScheduler = new TimerSchedulerImpl(timerClient, componentCallMetadata)

  override def eventSubject(): Optional[String] =
    if (metadata.isCloudEvent)
      metadata.asCloudEvent().subject()
    else
      Optional.empty()

  override def componentCallMetadata: MetadataImpl = {
    telemetryContext.fold(MetadataImpl.Empty)(MetadataImpl.Empty.withTelemetryContext)
  }

  override def tracing(): Tracing = new SpanTracingImpl(telemetryContext, tracerFactory)

}
