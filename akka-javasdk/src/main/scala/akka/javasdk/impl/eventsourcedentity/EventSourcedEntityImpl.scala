/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.eventsourcedentity

import java.util.Optional

import scala.concurrent.Future
import scala.util.control.NonFatal

import akka.annotation.InternalApi
import akka.javasdk.Metadata
import akka.javasdk.Tracing
import akka.javasdk.UserException
import akka.javasdk.eventsourcedentity.CommandContext
import akka.javasdk.eventsourcedentity.EventContext
import akka.javasdk.eventsourcedentity.EventSourcedEntity
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext
import akka.javasdk.impl.AbstractContext
import akka.javasdk.impl.ComponentDescriptor
import akka.javasdk.impl.ComponentType
import akka.javasdk.impl.EntityExceptions.EntityException
import akka.javasdk.impl.ErrorHandling.BadRequestException
import akka.javasdk.impl.MetadataImpl
import akka.javasdk.impl.effect.ErrorReplyImpl
import akka.javasdk.impl.effect.MessageReplyImpl
import akka.javasdk.impl.effect.NoSecondaryEffectImpl
import akka.javasdk.impl.eventsourcedentity.EventSourcedEntityEffectImpl.EmitEvents
import akka.javasdk.impl.eventsourcedentity.EventSourcedEntityEffectImpl.NoPrimaryEffect
import akka.javasdk.impl.serialization.JsonSerializer
import akka.javasdk.impl.telemetry.EventSourcedEntityCategory
import akka.javasdk.impl.telemetry.SpanTracingImpl
import akka.javasdk.impl.telemetry.Telemetry
import akka.javasdk.impl.telemetry.TraceInstrumentation
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
private[impl] object EventSourcedEntityImpl {

  private class CommandContextImpl(
      override val entityId: String,
      override val sequenceNumber: Long,
      override val commandName: String,
      override val isDeleted: Boolean,
      override val selfRegion: String,
      override val metadata: Metadata,
      span: Option[Span],
      tracerFactory: () => Tracer)
      extends AbstractContext
      with CommandContext {
    override def tracing(): Tracing = new SpanTracingImpl(span, tracerFactory)

    override def commandId(): Long = 0
  }

  private class EventSourcedEntityContextImpl(override final val entityId: String, override val selfRegion: String)
      extends AbstractContext
      with EventSourcedEntityContext

  private final class EventContextImpl(
      entityId: String,
      override val sequenceNumber: Long,
      override val selfRegion: String)
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

  private val traceInstrumentation = new TraceInstrumentation(componentId, EventSourcedEntityCategory, tracerFactory)

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

    val span: Option[Span] =
      traceInstrumentation.buildEntityCommandSpan(ComponentType.EventSourcedEntity, componentId, entityId, command)
    span.foreach(s => MDC.put(Telemetry.TRACE_ID, s.getSpanContext.getTraceId))
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
        span,
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
          case ErrorReplyImpl(userException) =>
            Left(new SpiEntity.Error(userException.getMessage, Some(serializer.toBytes(userException))))
          case MessageReplyImpl(message, m) =>
            val replyPayload = serializer.toBytes(message)
            val metadata = MetadataImpl.toSpi(m)
            Right(replyPayload -> metadata)
          case NoSecondaryEffectImpl =>
            throw new IllegalStateException("Expected reply or error")
        }
      }

      var currentSequence = command.sequenceNumber
      commandEffect.primaryEffect match {
        case EmitEvents(events, deleteEntity) =>
          var updatedState = state
          events.foreach { event =>
            updatedState = entityHandleEvent(updatedState, event.asInstanceOf[AnyRef], currentSequence)
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
                  deleteEntity))
          }

        case NoPrimaryEffect =>
          errorOrReply(state) match {
            case Left(err) =>
              Future.successful(new SpiEventSourcedEntity.ErrorEffect(err))
            case Right((reply, metadata)) =>
              Future.successful(new SpiEventSourcedEntity.ReplyEffect(reply, metadata))
          }
      }

    } catch {
      case e: UserException =>
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

      span.foreach { s =>
        MDC.remove(Telemetry.TRACE_ID)
        s.end()
      }
    }

  }

  override def handleEvent(
      state: SpiEventSourcedEntity.State,
      eventEnv: SpiEventSourcedEntity.EventEnvelope): SpiEventSourcedEntity.State = {
    // all event types are preemptively registered to the serializer by the ReflectiveEventSourcedEntityRouter
    val event = serializer.fromBytes(eventEnv.payload)
    entityHandleEvent(state, event, eventEnv.sequenceNumber)
  }

  def entityHandleEvent(
      state: SpiEventSourcedEntity.State,
      event: AnyRef,
      sequenceNumber: Long): SpiEventSourcedEntity.State = {
    val eventContext = new EventContextImpl(entityId, sequenceNumber, regionInfo.selfRegion)
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
}
