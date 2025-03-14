/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
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
import akka.javasdk.impl.ComponentDescriptor
import akka.javasdk.impl.ComponentType
import akka.javasdk.impl.ErrorHandling
import akka.javasdk.impl.MetadataImpl
import akka.javasdk.impl.consumer.ConsumerEffectImpl.AsyncEffect
import akka.javasdk.impl.consumer.ConsumerEffectImpl.ConsumedEffect
import akka.javasdk.impl.consumer.ConsumerEffectImpl.ProduceEffect
import akka.javasdk.impl.serialization.JsonSerializer
import akka.javasdk.impl.telemetry.ConsumerCategory
import akka.javasdk.impl.telemetry.SpanTracingImpl
import akka.javasdk.impl.telemetry.Telemetry
import akka.javasdk.impl.telemetry.TraceInstrumentation
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
import akka.runtime.sdk.spi.SpiMetadataEntry
import akka.runtime.sdk.spi.TimerClient
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC

@InternalApi
private[impl] final class ConsumerImpl[C <: Consumer](
    componentId: String,
    val factory: () => C,
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
  private val traceInstrumentation = new TraceInstrumentation(componentId, ConsumerCategory, tracerFactory)

  private val resultSerializer =
    // producing to topic, external json format, so mapper configurable by user
    if (consumerDestination.exists(_.isInstanceOf[TopicDestination])) new JsonSerializer(JsonSupport.getObjectMapper)
    // non-topic is internal, so non-configurable (also means no output json is ever passed anywhere though)
    else internalSerializer

  private def createRouter(): ReflectiveConsumerRouter[C] =
    new ReflectiveConsumerRouter[C](
      factory(),
      componentDescriptor.methodInvokers,
      internalSerializer,
      ignoreUnknown,
      consumesFromTopic = consumerSource.isInstanceOf[TopicSource])

  override def handleMessage(message: Message): Future[Effect] = {
    val metadata = MetadataImpl.of(message.metadata)

    // FIXME would be good if we could record the chosen method in the span
    val span: Option[Span] =
      traceInstrumentation.buildSpan(ComponentType.Consumer, componentId, metadata.subjectScala, message.metadata)

    val updatedMetadata = span.map(metadata.withTracing).getOrElse(metadata)

    span.foreach(s => MDC.put(Telemetry.TRACE_ID, s.getSpanContext.getTraceId))
    val fut =
      try {
        val messageContext =
          new MessageContextImpl(
            updatedMetadata,
            timerClient,
            tracerFactory,
            span,
            regionInfo.selfRegion,
            message.originRegion.toJava)

        val payload: BytesPayload = message.payload.getOrElse(throw new IllegalArgumentException("No message payload"))
        val effect = createRouter()
          .handleCommand(MessageEnvelope.of(payload, messageContext.metadata), messageContext)
        toSpiEffect(message, effect)
      } catch {
        case NonFatal(ex) =>
          // command handler threw an "unexpected" error, also covers HandlerNotFoundException
          Future.successful(handleUnexpectedException(message, ex))
      } finally {
        MDC.remove(Telemetry.TRACE_ID)
      }
    fut.andThen { case _ =>
      span.foreach(_.end())
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
    val span: Option[Span],
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
    if (metadata.has(Telemetry.TRACE_PARENT_KEY)) {
      MetadataImpl.of(
        List(new SpiMetadataEntry(Telemetry.TRACE_PARENT_KEY, metadata.get(Telemetry.TRACE_PARENT_KEY).get())))
    } else {
      MetadataImpl.Empty
    }
  }

  override def tracing(): Tracing = new SpanTracingImpl(span, tracerFactory)

}
