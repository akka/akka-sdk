/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.consumer

import java.util.Optional
import akka.actor.ActorSystem
import akka.annotation.InternalApi
import akka.javasdk.Metadata
import akka.javasdk.consumer.ConsumerContext
import akka.javasdk.consumer.MessageContext
import akka.javasdk.consumer.MessageEnvelope
import akka.javasdk.impl.AbstractContext
import akka.javasdk.impl.ConsumerFactory
import akka.javasdk.impl.MessageCodec
import akka.javasdk.impl.MetadataImpl
import akka.javasdk.impl.ResolvedEntityFactory
import akka.javasdk.impl.ResolvedServiceMethod
import akka.javasdk.impl.Service
import akka.javasdk.impl.telemetry.Instrumentation
import akka.javasdk.impl.telemetry.Telemetry
import akka.javasdk.impl.timer.TimerSchedulerImpl
import akka.javasdk.timer.TimerScheduler
import akka.runtime.sdk.spi.TimerClient
import com.google.protobuf.Descriptors
import io.opentelemetry.api.trace.Tracer
import kalix.protocol.action.Actions
import kalix.protocol.component.MetadataEntry
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * INTERNAL API
 */
@InternalApi
private[impl] final class ConsumerService(
    val factory: ConsumerFactory,
    override val descriptor: Descriptors.ServiceDescriptor,
    override val additionalDescriptors: Array[Descriptors.FileDescriptor],
    val messageCodec: MessageCodec)
    extends Service {

  @volatile var consumerClass: Option[Class[_]] = None

  def createConsumer(context: ConsumerContext): ConsumerRouter[_] = {
    val handler = factory.create(context)
    consumerClass = Some(handler.consumerClass())
    handler
  }

  def log: Logger = consumerClass match {
    case Some(clazz) => LoggerFactory.getLogger(clazz)
    case None        => LoggerFactory.getLogger("akka.javasdk.impl.consumer.ConsumersImpl")
  }

  override def resolvedMethods: Option[Map[String, ResolvedServiceMethod[_, _]]] =
    factory match {
      case resolved: ResolvedEntityFactory => Some(resolved.resolvedMethods)
      case _                               => None
    }

  //TODO???
  override final val componentType = Actions.name
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
    instrumentation: Instrumentation)
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

  override def getTracer: Tracer =
    instrumentation.getTracer

}

/**
 * INTERNAL API
 */
@InternalApi
private[impl] final class ConsumerContextImpl(val system: ActorSystem) extends AbstractContext with ConsumerContext {}
