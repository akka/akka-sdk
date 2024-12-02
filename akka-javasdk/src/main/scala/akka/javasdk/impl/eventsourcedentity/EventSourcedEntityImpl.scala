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
import akka.javasdk.impl.JsonMessageCodec
import akka.javasdk.impl.MetadataImpl
import akka.javasdk.impl.Settings
import akka.javasdk.impl.effect.ErrorReplyImpl
import akka.javasdk.impl.effect.MessageReplyImpl
import akka.javasdk.impl.effect.NoSecondaryEffectImpl
import akka.javasdk.impl.eventsourcedentity.EventSourcedEntityEffectImpl.EmitEvents
import akka.javasdk.impl.eventsourcedentity.EventSourcedEntityEffectImpl.NoPrimaryEffect
import akka.javasdk.impl.eventsourcedentity.EventSourcedEntityRouter.CommandHandlerNotFound
import akka.javasdk.impl.eventsourcedentity.EventSourcedEntityRouter.EventHandlerNotFound
import akka.javasdk.impl.telemetry.SpanTracingImpl
import akka.javasdk.impl.telemetry.Telemetry
import akka.runtime.sdk.spi.SpiEntity
import akka.runtime.sdk.spi.SpiEventSourcedEntity
import com.google.protobuf.ByteString
import com.google.protobuf.any.{ Any => ScalaPbAny }
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
      override val commandId: Long, // FIXME remove
      override val isDeleted: Boolean,
      override val metadata: Metadata,
      span: Option[Span],
      tracerFactory: () => Tracer)
      extends AbstractContext
      with CommandContext
      with ActivatableContext {
    override def tracing(): Tracing = new SpanTracingImpl(span, tracerFactory)
  }

  private class EventSourcedEntityContextImpl(override final val entityId: String)
      extends AbstractContext
      with EventSourcedEntityContext

  private final class EventContextImpl(entityId: String, override val sequenceNumber: Long)
      extends EventSourcedEntityContextImpl(entityId)
      with EventContext
}

/**
 * INTERNAL API
 */
@InternalApi
private[impl] final class EventSourcedEntityImpl[S, E, ES <: EventSourcedEntity[S, E]](
    configuration: Settings,
    tracerFactory: () => Tracer,
    componentId: String,
    componentClass: Class[_],
    entityId: String,
    messageCodec: JsonMessageCodec,
    factory: EventSourcedEntityContext => ES)
    extends SpiEventSourcedEntity {
  import EventSourcedEntityImpl._

  // FIXME
//  private val traceInstrumentation = new TraceInstrumentation(componentId, EventSourcedEntityCategory, tracerFactory)

  private val componentDescriptor = ComponentDescriptor.descriptorFor(componentClass, messageCodec)

  // FIXME remove EventSourcedEntityRouter altogether, and only keep stateless ReflectiveEventSourcedEntityRouter
  private val router: ReflectiveEventSourcedEntityRouter[AnyRef, AnyRef, EventSourcedEntity[AnyRef, AnyRef]] = {
    val context = new EventSourcedEntityContextImpl(entityId)
    new ReflectiveEventSourcedEntityRouter[S, E, ES](
      factory(context),
      componentDescriptor.commandHandlers,
      messageCodec)
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
    val cmd =
      messageCodec.decodeMessage(
        command.payload.getOrElse(
          // FIXME smuggling 0 arity method called from component client through here
          ScalaPbAny.defaultInstance.withTypeUrl(AnySupport.JsonTypeUrlPrefix).withValue(ByteString.empty())))
    val metadata: Metadata =
      MetadataImpl.Empty // FIXME MetadataImpl.of(command.metadata.map(_.entries.toVector).getOrElse(Nil))
    val cmdContext =
      new CommandContextImpl(
        entityId,
        command.sequenceNumber,
        command.name,
        0,
        command.isDeleted,
        metadata,
        span,
        tracerFactory)

    entity._internalSetCommandContext(Optional.of(cmdContext))
    try {
      entity._internalSetCurrentState(state)
      val commandEffect = router
        .handleCommand(command.name, state, cmd, cmdContext)
        .asInstanceOf[EventSourcedEntityEffectImpl[AnyRef, E]] // FIXME improve?

      def replyOrError(updatedState: SpiEventSourcedEntity.State): (Option[ScalaPbAny], Option[SpiEntity.Error]) = {
        commandEffect.secondaryEffect(updatedState) match {
          case ErrorReplyImpl(description) =>
            (None, Some(new SpiEntity.Error(description)))
          case MessageReplyImpl(message, _) =>
            // FIXME metadata?
            // FIXME is this encoding correct?
            val replyPayload = ScalaPbAny.fromJavaProto(messageCodec.encodeJava(message))
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
            updatedState = entityHandleEvent(updatedState, event.asInstanceOf[AnyRef], entityId, currentSequence)
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

            val serializedEvents =
              events.map(event => ScalaPbAny.fromJavaProto(messageCodec.encodeJava(event))).toVector

            Future.successful(
              new SpiEventSourcedEntity.Effect(events = serializedEvents, updatedState, reply, error, delete))
          }

        case NoPrimaryEffect =>
          val (reply, error) = replyOrError(state)

          Future.successful(
            new SpiEventSourcedEntity.Effect(events = Vector.empty, updatedState = state, reply, error, None))
      }

    } catch {
      case CommandHandlerNotFound(name) =>
        throw new EntityExceptions.EntityException(
          entityId,
          0, // FIXME remove commandId
          command.name,
          s"No command handler found for command [$name] on ${entity.getClass}")
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
          commandId = 0,
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
    val event =
      messageCodec
        .decodeMessage(eventEnv.payload)
        .asInstanceOf[AnyRef] // FIXME empty?
    entityHandleEvent(state, event, entityId, eventEnv.sequenceNumber)
  }

  def entityHandleEvent(
      state: SpiEventSourcedEntity.State,
      event: AnyRef,
      entityId: String,
      sequenceNumber: Long): SpiEventSourcedEntity.State = {
    val eventContext = new EventContextImpl(entityId, sequenceNumber)
    entity._internalSetEventContext(Optional.of(eventContext))
    try {
      router.handleEvent(state, event)
    } catch {
      case EventHandlerNotFound(eventClass) =>
        throw new IllegalArgumentException(s"Unknown event type [$eventClass] on ${entity.getClass}")
    } finally {
      entity._internalSetEventContext(Optional.empty())
    }
  }

  override def stateToProto(obj: SpiEventSourcedEntity.State): ScalaPbAny =
    ScalaPbAny.fromJavaProto(messageCodec.encodeJava(obj))

  override def stateFromProto(pb: ScalaPbAny): SpiEventSourcedEntity.State =
    messageCodec.decodeMessage(router.entityStateType, pb)
}
