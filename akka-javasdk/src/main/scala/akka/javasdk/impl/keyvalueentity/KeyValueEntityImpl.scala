/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.keyvalueentity

import java.util.Optional

import scala.concurrent.Future
import scala.util.control.NonFatal

import akka.annotation.InternalApi
import akka.javasdk.Metadata
import akka.javasdk.Tracing
import akka.javasdk.impl.AbstractContext
import akka.javasdk.impl.ActivatableContext
import akka.javasdk.impl.ComponentDescriptor
import akka.javasdk.impl.ComponentType
import akka.javasdk.impl.EntityExceptions.EntityException
import akka.javasdk.impl.ErrorHandling.BadRequestException
import akka.javasdk.impl.MetadataImpl
import akka.javasdk.impl.Settings
import akka.javasdk.impl.effect.ErrorReplyImpl
import akka.javasdk.impl.effect.MessageReplyImpl
import akka.javasdk.impl.effect.NoSecondaryEffectImpl
import akka.javasdk.impl.serialization.JsonSerializer
import akka.javasdk.impl.telemetry.KeyValueEntityCategory
import akka.javasdk.impl.telemetry.SpanTracingImpl
import akka.javasdk.impl.telemetry.Telemetry
import akka.javasdk.impl.telemetry.TraceInstrumentation
import akka.javasdk.keyvalueentity.CommandContext
import akka.javasdk.keyvalueentity.KeyValueEntity
import akka.javasdk.keyvalueentity.KeyValueEntityContext
import akka.runtime.sdk.spi.BytesPayload
import akka.runtime.sdk.spi.RegionInfo
import akka.runtime.sdk.spi.SpiEntity
import akka.runtime.sdk.spi.SpiEventSourcedEntity
import akka.runtime.sdk.spi.SpiMetadata
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import org.slf4j.MDC

/**
 * INTERNAL API
 */
@InternalApi
private[impl] object KeyValueEntityImpl {

  private class CommandContextImpl(
      override val entityId: String,
      override val commandName: String,
      override val selfRegion: String,
      override val metadata: Metadata,
      span: Option[Span],
      tracerFactory: () => Tracer)
      extends AbstractContext
      with CommandContext
      with ActivatableContext {
    override def tracing(): Tracing = new SpanTracingImpl(span, tracerFactory)

    override def commandId(): Long = 0
  }

  private class KeyValueEntityContextImpl(override final val entityId: String, override val selfRegion: String)
      extends AbstractContext
      with KeyValueEntityContext

}

/**
 * INTERNAL API
 */
@InternalApi
private[impl] final class KeyValueEntityImpl[S, KV <: KeyValueEntity[S]](
    configuration: Settings,
    tracerFactory: () => Tracer,
    componentId: String,
    entityId: String,
    serializer: JsonSerializer,
    componentDescriptor: ComponentDescriptor,
    entityStateType: Class[S],
    regionInfo: RegionInfo,
    factory: KeyValueEntityContext => KV)
    extends SpiEventSourcedEntity {
  import KeyValueEntityEffectImpl._
  import KeyValueEntityImpl._

  private val traceInstrumentation = new TraceInstrumentation(componentId, KeyValueEntityCategory, tracerFactory)

  private val router: ReflectiveKeyValueEntityRouter[AnyRef, KeyValueEntity[AnyRef]] = {
    val context = new KeyValueEntityContextImpl(entityId, regionInfo.selfRegion)
    new ReflectiveKeyValueEntityRouter[S, KV](factory(context), componentDescriptor.methodInvokers, serializer)
      .asInstanceOf[ReflectiveKeyValueEntityRouter[AnyRef, KeyValueEntity[AnyRef]]]
  }

  private def entity: KeyValueEntity[AnyRef] =
    router.entity

  override def emptyState: SpiEventSourcedEntity.State =
    entity.emptyState()

  override def handleCommand(
      state: SpiEventSourcedEntity.State,
      command: SpiEntity.Command): Future[SpiEventSourcedEntity.Effect] = {

    val span: Option[Span] =
      traceInstrumentation.buildEntityCommandSpan(ComponentType.KeyValueEntity, componentId, entityId, command)
    span.foreach(s => MDC.put(Telemetry.TRACE_ID, s.getSpanContext.getTraceId))
    // smuggling 0 arity method called from component client through here
    val cmdPayload = command.payload.getOrElse(BytesPayload.empty)
    val metadata: Metadata = MetadataImpl.of(command.metadata)
    val cmdContext =
      new CommandContextImpl(entityId, command.name, regionInfo.selfRegion, metadata, span, tracerFactory)

    try {
      entity._internalSetCommandContext(Optional.of(cmdContext))
      entity._internalSetCurrentState(state, command.isDeleted)
      val commandEffect = router
        .handleCommand(command.name, cmdPayload)
        .asInstanceOf[KeyValueEntityEffectImpl[AnyRef]] // FIXME improve?

      def errorOrReply: Either[SpiEntity.Error, (BytesPayload, SpiMetadata)] = {
        commandEffect.secondaryEffect match {
          case ErrorReplyImpl(description) =>
            Left(new SpiEntity.Error(description))
          case MessageReplyImpl(message, m) =>
            val replyPayload = serializer.toBytes(message)
            val metadata = MetadataImpl.toSpi(m)
            Right(replyPayload -> metadata)
          case NoSecondaryEffectImpl =>
            throw new IllegalStateException("Expected reply or error")
        }
      }

      commandEffect.primaryEffect match {
        case UpdateState(updatedState) =>
          errorOrReply match {
            case Left(err) =>
              Future.successful(new SpiEventSourcedEntity.ErrorEffect(err))
            case Right((reply, metadata)) =>
              val serializedState = serializer.toBytes(updatedState)

              Future.successful(
                new SpiEventSourcedEntity.PersistEffect(
                  events = Vector(serializedState),
                  updatedState,
                  reply,
                  metadata,
                  delete = None))
          }

        case DeleteEntity =>
          errorOrReply match {
            case Left(err) =>
              Future.successful(new SpiEventSourcedEntity.ErrorEffect(err))
            case Right((reply, metadata)) =>
              val delete = Some(configuration.cleanupDeletedEventSourcedEntityAfter)
              Future.successful(
                new SpiEventSourcedEntity.PersistEffect(events = Vector.empty, null, reply, metadata, delete))
          }

        case NoPrimaryEffect =>
          errorOrReply match {
            case Left(err) =>
              Future.successful(new SpiEventSourcedEntity.ErrorEffect(err))
            case Right((reply, metadata)) =>
              Future.successful(new SpiEventSourcedEntity.ReplyEffect(reply, metadata))
          }
      }

    } catch {
      case BadRequestException(msg) =>
        Future.successful(new SpiEventSourcedEntity.ErrorEffect(error = new SpiEntity.Error(msg)))
      case e: EntityException =>
        throw e
      case NonFatal(error) =>
        // also covers HandlerNotFoundException
        throw EntityException(
          entityId = entityId,
          commandName = command.name,
          s"Unexpected failure: $error",
          Some(error))
    } finally {
      entity._internalSetCommandContext(Optional.empty())
      entity._internalClearCurrentState()
      cmdContext.deactivate() // Very important!

      span.foreach { s =>
        MDC.remove(Telemetry.TRACE_ID)
        s.end()
      }
    }

  }

  override def handleEvent(
      state: SpiEventSourcedEntity.State,
      eventEnv: SpiEventSourcedEntity.EventEnvelope): SpiEventSourcedEntity.State = {
    throw new IllegalStateException("handleEvent not expected for KeyValueEntity")
  }

  override def stateToBytes(obj: SpiEventSourcedEntity.State): BytesPayload =
    serializer.toBytes(obj)

  override def stateFromBytes(pb: BytesPayload): SpiEventSourcedEntity.State =
    serializer.fromBytes(entityStateType, pb).asInstanceOf[SpiEventSourcedEntity.State]
}
