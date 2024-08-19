/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.impl.consumer

import java.util.Optional

import akka.actor.ActorSystem
import akka.annotation.InternalApi
import akka.platform.javasdk.Metadata
import akka.platform.javasdk.consumer.ConsumerContext
import akka.platform.javasdk.consumer.ConsumerOptions
import akka.platform.javasdk.consumer.MessageContext
import akka.platform.javasdk.consumer.MessageEnvelope
import akka.platform.javasdk.impl.ComponentOptions
import akka.platform.javasdk.impl.MessageCodec
import akka.platform.javasdk.impl.MetadataImpl
import akka.platform.javasdk.impl.Service
import akka.platform.javasdk.impl._
import akka.platform.javasdk.impl.telemetry.Instrumentation
import akka.platform.javasdk.impl.telemetry.Telemetry
import akka.platform.javasdk.impl.timer.TimerSchedulerImpl
import akka.platform.javasdk.spi.TimerClient
import akka.platform.javasdk.timer.TimerScheduler
import com.google.protobuf.Descriptors
import io.opentelemetry.api.trace.Tracer
import kalix.protocol.action.Actions
import kalix.protocol.component.MetadataEntry
import org.slf4j.Logger
import org.slf4j.LoggerFactory

final class ConsumerService(
    val factory: ConsumerFactory,
    override val descriptor: Descriptors.ServiceDescriptor,
    override val additionalDescriptors: Array[Descriptors.FileDescriptor],
    val messageCodec: MessageCodec,
    val consumerOptions: Option[ConsumerOptions])
    extends Service {

  def this(
      factory: ConsumerFactory,
      descriptor: Descriptors.ServiceDescriptor,
      additionalDescriptors: Array[Descriptors.FileDescriptor],
      messageCodec: MessageCodec,
      consumerOptions: ConsumerOptions) =
    this(factory, descriptor, additionalDescriptors, messageCodec, Some(consumerOptions))

  @volatile var consumerClass: Option[Class[_]] = None

  def createConsumer(context: ConsumerContext): ConsumerRouter[_] = {
    val handler = factory.create(context)
    consumerClass = Some(handler.consumerClass())
    handler
  }

  def log: Logger = consumerClass match {
    case Some(clazz) => LoggerFactory.getLogger(clazz)
    case None        => LoggerFactory.getLogger("akka.platform.javasdk.impl.consumer.ConsumersImpl")
  }

  override def resolvedMethods: Option[Map[String, ResolvedServiceMethod[_, _]]] =
    factory match {
      case resolved: ResolvedEntityFactory => Some(resolved.resolvedMethods)
      case _                               => None
    }

  override def componentOptions: Option[ComponentOptions] = consumerOptions

  //TODO???
  override final val componentType = Actions.name
}

case class MessageEnvelopeImpl[T](payload: T, metadata: Metadata) extends MessageEnvelope[T]

/**
 * INTERNAL API
 */
@InternalApi
final class MessageContextImpl(
    override val metadata: Metadata,
    val messageCodec: MessageCodec,
    val system: ActorSystem,
    timerClient: TimerClient,
    instrumentation: Instrumentation)
    extends AbstractContext(system)
    with MessageContext {

  val timers: TimerScheduler = new TimerSchedulerImpl(messageCodec, system, timerClient, componentCallMetadata)

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

@InternalApi
final class ConsumerContextImpl(val system: ActorSystem) extends AbstractContext(system) with ConsumerContext {}
