/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.keyvalueentity

import java.util.Optional

import scala.concurrent.Future
import scala.util.control.NonFatal

import akka.annotation.InternalApi
import akka.javasdk.CommandException
import akka.javasdk.Metadata
import akka.javasdk.Tracing
import akka.javasdk.impl.AbstractContext
import akka.javasdk.impl.ComponentDescriptor
import akka.javasdk.impl.EntityExceptions.EntityException
import akka.javasdk.impl.ErrorHandling.BadRequestException
import akka.javasdk.impl.MetadataImpl
import akka.javasdk.impl.Settings
import akka.javasdk.impl.effect.ErrorReplyImpl
import akka.javasdk.impl.effect.MessageReplyImpl
import akka.javasdk.impl.effect.NoSecondaryEffectImpl
import akka.javasdk.impl.serialization.JsonSerializer
import akka.javasdk.impl.telemetry.SpanTracingImpl
import akka.javasdk.impl.telemetry.Telemetry
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
import io.opentelemetry.context.{ Context => OtelContext }
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
      telemetryContext: Option[OtelContext],
      tracerFactory: () => Tracer)
      extends AbstractContext
      with CommandContext {
    override def tracing(): Tracing = new SpanTracingImpl(telemetryContext, tracerFactory)

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

    val telemetryContext = Option(command.telemetryContext)
    val traceId = telemetryContext.flatMap { context =>
      Option(Span.fromContextOrNull(context)).map(_.getSpanContext.getTraceId)
    }
    traceId.foreach(id => MDC.put(Telemetry.TRACE_ID, id))

    // smuggling 0 arity method called from component client through here
    val cmdPayload = command.payload.getOrElse(BytesPayload.empty)
    val metadata: Metadata = MetadataImpl.of(command.metadata)
    val cmdContext =
      new CommandContextImpl(entityId, command.name, regionInfo.selfRegion, metadata, telemetryContext, tracerFactory)

    try {
      entity._internalSetCommandContext(Optional.of(cmdContext))
      entity._internalSetCurrentState(state, command.isDeleted)
      val commandEffect = router
        .handleCommand(command.name, cmdPayload)
        .asInstanceOf[KeyValueEntityEffectImpl[AnyRef]] // FIXME improve?

      def errorOrReply: Either[SpiEntity.Error, (BytesPayload, SpiMetadata)] = {
        commandEffect.secondaryEffect match {
          case ErrorReplyImpl(commandException) =>
            Left(new SpiEntity.Error(commandException.getMessage, Some(serializer.toBytes(commandException))))
          case MessageReplyImpl(message, m) =>
            val replyPayload = serializer.toBytes(message)
            val metadata = MetadataImpl.toSpi(m)
            Right(replyPayload -> metadata)
          case NoSecondaryEffectImpl =>
            throw new IllegalStateException("Expected reply or error")
        }
      }

      commandEffect.primaryEffect match {
        case UpdateState(updatedState, metadataOpt) =>
          errorOrReply match {
            case Left(err) =>
              Future.successful(new SpiEventSourcedEntity.ErrorEffect(err))
            case Right((reply, metadata)) =>
              val serializedState = serializer.toBytes(updatedState)
              val stateMetadata = metadataOpt.map(MetadataImpl.toSpi).toVector

              Future.successful(
                new SpiEventSourcedEntity.PersistEffect(
                  events = Vector(serializedState),
                  updatedState,
                  reply,
                  metadata,
                  deleteEntity = false,
                  stateMetadata))
          }

        case DeleteEntity =>
          errorOrReply match {
            case Left(err) =>
              Future.successful(new SpiEventSourcedEntity.ErrorEffect(err))
            case Right((reply, metadata)) =>
              Future.successful(
                new SpiEventSourcedEntity.PersistEffect(
                  events = Vector.empty,
                  null,
                  reply,
                  metadata,
                  deleteEntity = true,
                  Vector.empty))
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
      case e: CommandException =>
        val serializedException = serializer.toBytes(e)
        Future.successful(
          new SpiEventSourcedEntity.ErrorEffect(error = new SpiEntity.Error(e.getMessage, Some(serializedException))))
      case BadRequestException(msg) =>
        Future.successful(new SpiEventSourcedEntity.ErrorEffect(error = new SpiEntity.Error(msg, None)))
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
      if (traceId.isDefined) MDC.remove(Telemetry.TRACE_ID)
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
