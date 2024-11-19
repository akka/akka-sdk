/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.consumer

import akka.annotation.InternalApi
import akka.javasdk.Metadata
import akka.javasdk.Tracing
import akka.javasdk.consumer.Consumer
import akka.javasdk.consumer.MessageContext
import akka.javasdk.consumer.MessageEnvelope
import akka.javasdk.impl.AbstractContext
import akka.javasdk.impl.ComponentDescriptorFactory
import akka.javasdk.impl.JsonMessageCodec
import akka.javasdk.impl.MessageCodec
import akka.javasdk.impl.MetadataImpl
import akka.javasdk.impl.Service
import akka.javasdk.impl.telemetry.SpanTracingImpl
import akka.javasdk.impl.telemetry.Telemetry
import akka.javasdk.impl.timer.TimerSchedulerImpl
import akka.javasdk.timer.TimerScheduler
import akka.runtime.sdk.spi.TimerClient
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import kalix.protocol.action.Actions
import kalix.protocol.component.MetadataEntry
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.util.Optional

/**
 * INTERNAL API
 */
@InternalApi
private[impl] class ConsumerService[A <: Consumer](
    consumerClass: Class[_],
    messageCodec: JsonMessageCodec,
    factory: () => A)
    extends Service(consumerClass, Actions.name, messageCodec) {

  lazy val log: Logger = LoggerFactory.getLogger(consumerClass)

  def createRouter(): ConsumerRouter[A] =
    new ReflectiveConsumerRouter[A](
      factory(),
      componentDescriptor.commandHandlers,
      ComponentDescriptorFactory.findIgnore(consumerClass))

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
    val messageCodec: MessageCodec,
    timerClient: TimerClient,
    tracerFactory: () => Tracer,
    span: Option[Span])
    extends AbstractContext
    with MessageContext {

  val timers: TimerScheduler = new TimerSchedulerImpl(messageCodec, timerClient, componentCallMetadata)

  override def eventSubject(): Optional[String] =
    if (metadata.isCloudEvent)
      metadata.asCloudEvent().subject()
    else
      Optional.empty()

  override def componentCallMetadata: MetadataImpl = {
    if (metadata.has(Telemetry.TRACE_PARENT_KEY)) {
      MetadataImpl.of(
        List(
          MetadataEntry(
            Telemetry.TRACE_PARENT_KEY,
            MetadataEntry.Value.StringValue(metadata.get(Telemetry.TRACE_PARENT_KEY).get()))))
    } else {
      MetadataImpl.Empty
    }
  }

  override def tracing(): Tracing = new SpanTracingImpl(span, tracerFactory)
}
