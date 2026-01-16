/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.eventsourcedentity

import java.util.Optional

import scala.concurrent.Future
import scala.util.control.NonFatal

import akka.annotation.InternalApi
import akka.javasdk.CommandException
import akka.javasdk.Metadata
import akka.javasdk.Tracing
import akka.javasdk.eventsourcedentity.CommandContext
import akka.javasdk.eventsourcedentity.EventContext
import akka.javasdk.eventsourcedentity.EventSourcedEntity
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext
import akka.javasdk.impl.AbstractContext
import akka.javasdk.impl.ComponentDescriptor
import akka.javasdk.impl.EntityExceptions.EntityException
import akka.javasdk.impl.ErrorHandling.BadRequestException
import akka.javasdk.impl.MetadataImpl
import akka.javasdk.impl.effect.ErrorReplyImpl
import akka.javasdk.impl.effect.MessageReplyImpl
import akka.javasdk.impl.effect.NoSecondaryEffectImpl
import akka.javasdk.impl.effect.ReplicationFilterImpl
import akka.javasdk.impl.eventsourcedentity.EventSourcedEntityEffectImpl.EmitEvents
import akka.javasdk.impl.eventsourcedentity.EventSourcedEntityEffectImpl.EmitEventsWithMetadata
import akka.javasdk.impl.eventsourcedentity.EventSourcedEntityEffectImpl.NoPrimaryEffect
import akka.javasdk.impl.reflection.Reflect
import akka.javasdk.impl.serialization.JsonSerializer
import akka.javasdk.impl.telemetry.SpanTracingImpl
import akka.javasdk.impl.telemetry.Telemetry
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
private[impl] object EventSourcedEntityImpl {

  private class CommandContextImpl(
      override val entityId: String,
      override val sequenceNumber: Long,
      override val commandName: String,
      override val isDeleted: Boolean,
      override val selfRegion: String,
      override val metadata: Metadata,
      telemetryContext: Option[OtelContext],
      tracerFactory: () => Tracer)
      extends AbstractContext
      with CommandContext {
    override def tracing(): Tracing = new SpanTracingImpl(telemetryContext, tracerFactory)

    override def commandId(): Long = 0
  }

  private class EventSourcedEntityContextImpl(override final val entityId: String, override val selfRegion: String)
      extends AbstractContext
      with EventSourcedEntityContext

  private final class EventContextImpl(
      entityId: String,
      override val sequenceNumber: Long,
      override val selfRegion: String,
      override val metadata: Metadata)
      extends EventSourcedEntityContextImpl(entityId, selfRegion)
      with EventContext

}

/**
 * INTERNAL API
 */
@InternalApi
private[impl] final class EventSourcedEntityImpl[S, E, ES <: EventSourcedEntity[S, E]](
    tracerFactory: () => Tracer,
    componentId: String,
    entityId: String,
    serializer: JsonSerializer,
    componentDescriptor: ComponentDescriptor,
    entityStateType: Class[S],
    regionInfo: RegionInfo,
    factory: EventSourcedEntityContext => ES)
    extends SpiEventSourcedEntity {
  import EventSourcedEntityImpl._

  private val router: ReflectiveEventSourcedEntityRouter[AnyRef, AnyRef, EventSourcedEntity[AnyRef, AnyRef]] = {
    val context = new EventSourcedEntityContextImpl(entityId, regionInfo.selfRegion)
    new ReflectiveEventSourcedEntityRouter[S, E, ES](factory(context), componentDescriptor.methodInvokers, serializer)
      .asInstanceOf[ReflectiveEventSourcedEntityRouter[AnyRef, AnyRef, EventSourcedEntity[AnyRef, AnyRef]]]
  }

  private def entity: EventSourcedEntity[AnyRef, AnyRef] =
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
      new CommandContextImpl(
        entityId,
        command.sequenceNumber,
        command.name,
        command.isDeleted,
        regionInfo.selfRegion,
        metadata,
        telemetryContext,
        tracerFactory)

    try {
      entity._internalSetCommandContext(Optional.of(cmdContext))
      entity._internalSetCurrentState(state, command.isDeleted)
      val commandEffect = router
        .handleCommand(command.name, cmdPayload)
        .asInstanceOf[EventSourcedEntityEffectImpl[AnyRef, E]] // FIXME improve?

      def errorOrReply(
          updatedState: SpiEventSourcedEntity.State): Either[SpiEntity.Error, (BytesPayload, SpiMetadata)] = {
        commandEffect.secondaryEffect(updatedState) match {
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

      if ((commandEffect.replFilter ne ReplicationFilterImpl.empty) && !isReplicationFilterEnabled) {
        throw new IllegalStateException(
          "To use replication filters the EventSourcedEntity class must be annotated with @EnableReplicationFilter.")
      }

      var currentSequence = command.sequenceNumber

      def emitEvents(events: Iterable[Any], eventsMetadata: Iterable[Metadata], deleteEntity: Boolean) = {
        var updatedState = state
        val eventsAndMetadata =
          if (eventsMetadata.isEmpty)
            events.map(_ -> Metadata.EMPTY)
          else
            events.zip(eventsMetadata)

        eventsAndMetadata.foreach { case (event, eventMetadata) =>
          updatedState = entityHandleEvent(updatedState, event.asInstanceOf[AnyRef], eventMetadata, currentSequence)
          if (updatedState == null)
            throw new IllegalArgumentException("Event handler must not return null as the updated state.")
          currentSequence += 1
        }

        errorOrReply(updatedState) match {
          case Left(err) =>
            Future.successful(new SpiEventSourcedEntity.ErrorEffect(err))
          case Right((reply, metadata)) =>
            val serializedEvents = events.map(event => serializer.toBytes(event)).toVector

            Future.successful(
              new SpiEventSourcedEntity.PersistEffect(
                events = serializedEvents,
                updatedState,
                reply,
                metadata,
                deleteEntity,
                eventsMetadata.iterator.map(MetadataImpl.toSpi).toVector,
                replicationFilter = commandEffect.replFilter.toSpi))
        }
      }

      commandEffect.primaryEffect match {
        case EmitEvents(events, deleteEntity) =>
          emitEvents(events, Vector.empty, deleteEntity)

        case EmitEventsWithMetadata(eventsWithMetadata, deleteEntity) =>
          val events = eventsWithMetadata.map(_.getEvent)
          val eventsMetadata = eventsWithMetadata.map(_.getMetadata)
          emitEvents(events, eventsMetadata, deleteEntity)

        case NoPrimaryEffect =>
          errorOrReply(state) match {
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
    // all event types are preemptively registered to the serializer by the ReflectiveEventSourcedEntityRouter
    val event = serializer.fromBytes(eventEnv.payload)
    val eventMetadata = MetadataImpl.of(eventEnv.eventMetadata)
    entityHandleEvent(state, event, eventMetadata, eventEnv.sequenceNumber)
  }

  def entityHandleEvent(
      state: SpiEventSourcedEntity.State,
      event: AnyRef,
      eventMetadata: Metadata,
      sequenceNumber: Long): SpiEventSourcedEntity.State = {
    val eventContext = new EventContextImpl(entityId, sequenceNumber, regionInfo.selfRegion, eventMetadata)
    entity._internalSetEventContext(Optional.of(eventContext))
    val clearState = entity._internalSetCurrentState(state, false)
    try {
      router.handleEvent(event)
    } finally {
      entity._internalSetEventContext(Optional.empty())
      if (clearState)
        entity._internalClearCurrentState()
    }
  }

  override def stateToBytes(obj: SpiEventSourcedEntity.State): BytesPayload =
    serializer.toBytes(obj)

  override def stateFromBytes(pb: BytesPayload): SpiEventSourcedEntity.State =
    serializer.fromBytes(entityStateType, pb).asInstanceOf[SpiEventSourcedEntity.State]

  private def isReplicationFilterEnabled: Boolean =
    Reflect.isReplicationFilterEnabled(entity.getClass)
}
