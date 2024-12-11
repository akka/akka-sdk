/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.eventsourcedentity

import java.util.Optional

import scala.concurrent.Future
import scala.util.control.NonFatal

import akka.annotation.InternalApi
import akka.javasdk.Metadata
import akka.javasdk.Tracing
import akka.javasdk.eventsourcedentity.CommandContext
import akka.javasdk.eventsourcedentity.EventContext
import akka.javasdk.eventsourcedentity.EventSourcedEntity
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext
import akka.javasdk.impl.AbstractContext
import akka.javasdk.impl.ActivatableContext
import akka.javasdk.impl.AnySupport
import akka.javasdk.impl.ComponentDescriptor
import akka.javasdk.impl.EntityExceptions
import akka.javasdk.impl.EntityExceptions.EntityException
import akka.javasdk.impl.ErrorHandling.BadRequestException
import akka.javasdk.impl.MetadataImpl
import akka.javasdk.impl.Settings
import akka.javasdk.impl.effect.ErrorReplyImpl
import akka.javasdk.impl.effect.MessageReplyImpl
import akka.javasdk.impl.effect.NoSecondaryEffectImpl
import akka.javasdk.impl.eventsourcedentity.EventSourcedEntityEffectImpl.EmitEvents
import akka.javasdk.impl.eventsourcedentity.EventSourcedEntityEffectImpl.NoPrimaryEffect
import akka.javasdk.impl.serialization.JsonSerializer
import akka.javasdk.impl.telemetry.SpanTracingImpl
import akka.javasdk.impl.telemetry.Telemetry
import akka.runtime.sdk.spi.BytesPayload
import akka.runtime.sdk.spi.SpiEntity
import akka.runtime.sdk.spi.SpiEventSourcedEntity
import akka.util.ByteString
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
      override val metadata: Metadata,
      span: Option[Span],
      tracerFactory: () => Tracer)
      extends AbstractContext
      with CommandContext
      with ActivatableContext {
    override def tracing(): Tracing = new SpanTracingImpl(span, tracerFactory)

    override def commandId(): Long = 0
  }

  private class EventSourcedEntityContextImpl(override final val entityId: String)
      extends AbstractContext
      with EventSourcedEntityContext

  private final class EventContextImpl(entityId: String, override val sequenceNumber: Long)
      extends EventSourcedEntityContextImpl(entityId)
      with EventContext

  // 0 arity method
  private val NoCommandPayload = new BytesPayload(ByteString.empty, AnySupport.JsonTypeUrlPrefix)
}

/**
 * INTERNAL API
 */
@InternalApi
private[impl] final class EventSourcedEntityImpl[S, E, ES <: EventSourcedEntity[S, E]](
    configuration: Settings,
    tracerFactory: () => Tracer,
    componentId: String,
    entityId: String,
    serializer: JsonSerializer,
    componentDescriptor: ComponentDescriptor,
    factory: EventSourcedEntityContext => ES)
    extends SpiEventSourcedEntity {
  import EventSourcedEntityImpl._

  // FIXME
//  private val traceInstrumentation = new TraceInstrumentation(componentId, EventSourcedEntityCategory, tracerFactory)

  private val router: ReflectiveEventSourcedEntityRouter[AnyRef, AnyRef, EventSourcedEntity[AnyRef, AnyRef]] = {
    val context = new EventSourcedEntityContextImpl(entityId)
    new ReflectiveEventSourcedEntityRouter[S, E, ES](factory(context), componentDescriptor.commandHandlers, serializer)
      .asInstanceOf[ReflectiveEventSourcedEntityRouter[AnyRef, AnyRef, EventSourcedEntity[AnyRef, AnyRef]]]
  }

  private def entity: EventSourcedEntity[AnyRef, AnyRef] =
    router.entity

  override def emptyState: SpiEventSourcedEntity.State =
    entity.emptyState()

  override def handleCommand(
      state: SpiEventSourcedEntity.State,
      command: SpiEntity.Command): Future[SpiEventSourcedEntity.Effect] = {

    val span: Option[Span] = None // FIXME traceInstrumentation.buildSpan(service, command)
    span.foreach(s => MDC.put(Telemetry.TRACE_ID, s.getSpanContext.getTraceId))
    val cmdPayload = command.payload.getOrElse(
      // smuggling 0 arity method called from component client through here
      NoCommandPayload)
    val metadata: Metadata = MetadataImpl.of(command.metadata)
    val cmdContext =
      new CommandContextImpl(
        entityId,
        command.sequenceNumber,
        command.name,
        command.isDeleted,
        metadata,
        span,
        tracerFactory)

    try {
      entity._internalSetCommandContext(Optional.of(cmdContext))
      entity._internalSetCurrentState(state)
      val commandEffect = router
        .handleCommand(command.name, cmdPayload, cmdContext)
        .asInstanceOf[EventSourcedEntityEffectImpl[AnyRef, E]] // FIXME improve?

      def replyOrError(updatedState: SpiEventSourcedEntity.State): (Option[BytesPayload], Option[SpiEntity.Error]) = {
        commandEffect.secondaryEffect(updatedState) match {
          case ErrorReplyImpl(description) =>
            (None, Some(new SpiEntity.Error(description)))
          case MessageReplyImpl(message, _) =>
            // FIXME metadata?
            val replyPayload = serializer.toBytes(message)
            (Some(replyPayload), None)
          case NoSecondaryEffectImpl =>
            (None, None)
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

          val (reply, error) = replyOrError(updatedState)

          if (error.isDefined) {
            Future.successful(
              new SpiEventSourcedEntity.Effect(events = Vector.empty, updatedState = state, reply = None, error, None))
          } else {
            val delete =
              if (deleteEntity) Some(configuration.cleanupDeletedEventSourcedEntityAfter)
              else None

            val serializedEvents = events.map(event => serializer.toBytes(event)).toVector

            Future.successful(
              new SpiEventSourcedEntity.Effect(events = serializedEvents, updatedState, reply, error, delete))
          }

        case NoPrimaryEffect =>
          val (reply, error) = replyOrError(state)

          Future.successful(
            new SpiEventSourcedEntity.Effect(events = Vector.empty, updatedState = state, reply, error, None))
      }

    } catch {
      case e: HandlerNotFoundException =>
        throw new EntityExceptions.EntityException(
          entityId,
          command.name,
          s"No command handler found for command [${e.name}] on ${entity.getClass}")
      case BadRequestException(msg) =>
        Future.successful(
          new SpiEventSourcedEntity.Effect(
            events = Vector.empty,
            updatedState = state,
            reply = None,
            error = Some(new SpiEntity.Error(msg)),
            delete = None))
      case e: EntityException =>
        throw e
      case NonFatal(error) =>
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
    // all event types are preemptively registered to the serializer by the ReflectiveEventSourcedEntityRouter
    val event = serializer.fromBytes(eventEnv.payload)
    entityHandleEvent(state, event, eventEnv.sequenceNumber)
  }

  def entityHandleEvent(
      state: SpiEventSourcedEntity.State,
      event: AnyRef,
      sequenceNumber: Long): SpiEventSourcedEntity.State = {
    val eventContext = new EventContextImpl(entityId, sequenceNumber)
    entity._internalSetEventContext(Optional.of(eventContext))
    val clearState = entity._internalSetCurrentState(state)
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
    serializer.fromBytes(router.entityStateType, pb)
}
