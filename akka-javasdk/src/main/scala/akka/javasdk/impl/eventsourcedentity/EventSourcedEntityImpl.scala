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
import akka.runtime.sdk.spi.SpiSerialization
import akka.runtime.sdk.spi.SpiSerialization.Deserialized
import com.google.protobuf.ByteString
import com.google.protobuf.any.{ Any => ScalaPbAny }
import io.grpc.Status
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import org.slf4j.LoggerFactory
import org.slf4j.MDC

/**
 * INTERNAL API
 */
@InternalApi
private[impl] object EventSourcedEntityImpl {
  private val log = LoggerFactory.getLogger(this.getClass)

  private class CommandContextImpl(
      override val entityId: String,
      override val sequenceNumber: Long,
      override val commandName: String,
      override val commandId: Long, // FIXME remove
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
    messageCodec: JsonMessageCodec,
    factory: EventSourcedEntityContext => ES,
    snapshotEvery: Int)
    extends SpiEventSourcedEntity {
  import EventSourcedEntityImpl._

  if (snapshotEvery < 0)
    log.warn("Snapshotting disabled for entity [{}], this is not recommended.", componentId)

  // FIXME
//  private val traceInstrumentation = new TraceInstrumentation(componentId, EventSourcedEntityCategory, tracerFactory)

  private val componentDescriptor = ComponentDescriptor.descriptorFor(componentClass, messageCodec)

  // FIXME remove EventSourcedEntityRouter altogether, and only keep stateless ReflectiveEventSourcedEntityRouter
  private def createRouter(context: EventSourcedEntityContext)
      : ReflectiveEventSourcedEntityRouter[AnyRef, AnyRef, EventSourcedEntity[AnyRef, AnyRef]] =
    new ReflectiveEventSourcedEntityRouter[S, E, ES](
      factory(context),
      componentDescriptor.commandHandlers,
      messageCodec)
      .asInstanceOf[ReflectiveEventSourcedEntityRouter[AnyRef, AnyRef, EventSourcedEntity[AnyRef, AnyRef]]]

  override def emptyState(entityId: String): SpiEventSourcedEntity.State = {
    // FIXME rather messy with the contexts here
    val cmdContext =
      new CommandContextImpl(entityId, 0L, "", 0, MetadataImpl.of(Nil), None, tracerFactory)
    val context = new EventSourcedEntityContextImpl(entityId)
    val router = createRouter(context)
    router.entity._internalSetCommandContext(Optional.of(cmdContext))
    try {
      router.entity.emptyState()
    } finally {
      router.entity._internalSetCommandContext(Optional.empty())
    }
  }

  override def handleCommand(
      state: SpiEventSourcedEntity.State,
      command: SpiEntity.Command): Future[SpiEventSourcedEntity.Effect] = {
    val entityId = command.entityId

    val span: Option[Span] = None // FIXME traceInstrumentation.buildSpan(service, command)
    span.foreach(s => MDC.put(Telemetry.TRACE_ID, s.getSpanContext.getTraceId))
    val cmd =
      messageCodec.decodeMessage(
        command.payload.getOrElse(
          // FIXME smuggling 0 arity method called from component client through here
          ScalaPbAny.defaultInstance.withTypeUrl(AnySupport.JsonTypeUrlPrefix).withValue(ByteString.empty())))
    val metadata: Metadata =
      MetadataImpl.of(Nil) // FIXME MetadataImpl.of(command.metadata.map(_.entries.toVector).getOrElse(Nil))
    val cmdContext =
      new CommandContextImpl(entityId, command.sequenceNumber, command.name, 0, metadata, span, tracerFactory)

    val context = new EventSourcedEntityContextImpl(entityId)
    val router = createRouter(context)
    router.entity._internalSetCommandContext(Optional.of(cmdContext))
    try {
      router.entity._internalSetCurrentState(state)
      val commandEffect = router
        .handleCommand(command.name, state, cmd, cmdContext)
        .asInstanceOf[EventSourcedEntityEffectImpl[AnyRef, E]] // FIXME improve?

      def replyOrError(updatedState: SpiEventSourcedEntity.State): (Option[ScalaPbAny], Option[SpiEntity.Error]) = {
        commandEffect.secondaryEffect(updatedState) match {
          case ErrorReplyImpl(description, status) =>
            val errorCode = status.map(_.value).getOrElse(Status.Code.UNKNOWN.value)
            (None, Some(new SpiEntity.Error(description, errorCode)))
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
      var updatedState = state
      commandEffect.primaryEffect match {
        case EmitEvents(events, deleteEntity) =>
          var shouldSnapshot = false
          events.foreach { event =>
            updatedState = entityHandleEvent(updatedState, event.asInstanceOf[AnyRef], entityId, currentSequence)
            if (updatedState == null)
              throw new IllegalArgumentException("Event handler must not return null as the updated state.")
            currentSequence += 1
            shouldSnapshot = shouldSnapshot || (snapshotEvery > 0 && currentSequence % snapshotEvery == 0)
          }

          val (reply, error) = replyOrError(updatedState)

          if (error.isDefined) {
            Future.successful(
              new SpiEventSourcedEntity.Effect(events = Vector.empty, updatedState = state, reply = None, error, None))
          } else {
            // snapshotting final state since that is the "atomic" write
            // emptyState can be null but null snapshot should not be stored, but that can't even
            // happen since event handler is not allowed to return null as newState
            // FIXME
//            val snapshot =
//              if (shouldSnapshot) Option(updatedState)
//              else None

            val delete =
              if (deleteEntity) Some(configuration.cleanupDeletedEventSourcedEntityAfter)
              else None

            val serializedEvents =
              events.map(event => ScalaPbAny.fromJavaProto(messageCodec.encodeJava(event))).toVector

            Future.successful(
              new SpiEventSourcedEntity.Effect(events = serializedEvents, updatedState = state, reply, error, delete))
          }

        case NoPrimaryEffect =>
          val (reply, error) = replyOrError(updatedState)

          Future.successful(
            new SpiEventSourcedEntity.Effect(events = Vector.empty, updatedState = state, reply, error, None))
      }

    } catch {
      case CommandHandlerNotFound(name) =>
        throw new EntityExceptions.EntityException(
          entityId,
          0, // FIXME remove commandId
          command.name,
          s"No command handler found for command [$name] on ${router.entity.getClass}")
      case BadRequestException(msg) =>
        Future.successful(
          new SpiEventSourcedEntity.Effect(
            events = Vector.empty,
            updatedState = state,
            reply = None,
            error = Some(new SpiEntity.Error(msg, Status.Code.INVALID_ARGUMENT.value)),
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
      router.entity._internalSetCommandContext(Optional.empty())
      router.entity._internalClearCurrentState()
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
    entityHandleEvent(state, event, eventEnv.entityId, eventEnv.sequenceNumber)
  }

  def entityHandleEvent(
      state: SpiEventSourcedEntity.State,
      event: AnyRef,
      entityId: String,
      sequenceNumber: Long): SpiEventSourcedEntity.State = {
    val eventContext = new EventContextImpl(entityId, sequenceNumber)
    val router = createRouter(eventContext) // FIXME reuse router instance?
    router.entity._internalSetEventContext(Optional.of(eventContext))
    try {
      router.handleEvent(state, event)
    } catch {
      case EventHandlerNotFound(eventClass) =>
        throw new IllegalArgumentException(s"Unknown event type [$eventClass] on ${router.entity.getClass}")
    } finally {
      router.entity._internalSetEventContext(Optional.empty())
    }
  }

  override val stateSerializer: SpiSerialization.Serializer =
    new SpiSerialization.Serializer {

      override def toProto(obj: Deserialized): ScalaPbAny =
        ScalaPbAny.fromJavaProto(messageCodec.encodeJava(obj))

      override def fromProto(pb: ScalaPbAny): Deserialized =
        messageCodec.decodeMessage(pb).asInstanceOf[Deserialized]
    }
}
