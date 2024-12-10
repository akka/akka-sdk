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
import akka.javasdk.impl.serialization.JsonSerializer
import akka.javasdk.impl.telemetry.SpanTracingImpl
import akka.javasdk.impl.telemetry.Telemetry
import akka.javasdk.keyvalueentity.CommandContext
import akka.javasdk.keyvalueentity.KeyValueEntity
import akka.javasdk.keyvalueentity.KeyValueEntityContext
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
private[impl] object KeyValueEntityImpl {

  private class CommandContextImpl(
      override val entityId: String,
      val sequenceNumber: Long,
      override val commandName: String,
      val isDeleted: Boolean,
      override val metadata: Metadata,
      span: Option[Span],
      tracerFactory: () => Tracer)
      extends AbstractContext
      with CommandContext
      with ActivatableContext {
    override def tracing(): Tracing = new SpanTracingImpl(span, tracerFactory)

    override def commandId(): Long = 0
  }

  private class KeyValueEntityContextImpl(override final val entityId: String)
      extends AbstractContext
      with KeyValueEntityContext

  // 0 arity method
  private val NoCommandPayload = new BytesPayload(ByteString.empty, AnySupport.JsonTypeUrlPrefix)
}

/**
 * INTERNAL API
 */
@InternalApi
private[impl] final class KeyValueEntityImpl[S, KV <: KeyValueEntity[S]](
    configuration: Settings,
    tracerFactory: () => Tracer,
    componentId: String,
    componentClass: Class[_],
    entityId: String,
    serializer: JsonSerializer,
    factory: KeyValueEntityContext => KV)
    extends SpiEventSourcedEntity {
  import KeyValueEntityEffectImpl._
  import KeyValueEntityImpl._

  // FIXME
//  private val traceInstrumentation = new TraceInstrumentation(componentId, EventSourcedEntityCategory, tracerFactory)

  private val componentDescriptor = ComponentDescriptor.descriptorFor(componentClass, serializer)

  private val router: ReflectiveKeyValueEntityRouter[AnyRef, KeyValueEntity[AnyRef]] = {
    val context = new KeyValueEntityContextImpl(entityId)
    new ReflectiveKeyValueEntityRouter[S, KV](factory(context), componentDescriptor.commandHandlers, serializer)
      .asInstanceOf[ReflectiveKeyValueEntityRouter[AnyRef, KeyValueEntity[AnyRef]]]
  }

  private def entity: KeyValueEntity[AnyRef] =
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
        .asInstanceOf[KeyValueEntityEffectImpl[AnyRef]] // FIXME improve?

      def replyOrError: (Option[BytesPayload], Option[SpiEntity.Error]) = {
        commandEffect.secondaryEffect match {
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

      commandEffect.primaryEffect match {
        case UpdateState(updatedState) =>
          val (reply, error) = replyOrError

          if (error.isDefined) {
            Future.successful(
              new SpiEventSourcedEntity.Effect(events = Vector.empty, updatedState = state, reply = None, error, None))
          } else {
            val serializedState = serializer.toBytes(updatedState)

            Future.successful(
              new SpiEventSourcedEntity.Effect(
                events = Vector(serializedState),
                updatedState,
                reply,
                error,
                delete = None))
          }

        case DeleteEntity =>
          val (reply, error) = replyOrError

          val delete = Some(configuration.cleanupDeletedEventSourcedEntityAfter)
          Future.successful(new SpiEventSourcedEntity.Effect(events = Vector.empty, null, reply, error, delete))

        case NoPrimaryEffect =>
          val (reply, error) = replyOrError

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
    throw new IllegalStateException("handleEvent not expected for KeyValueEntity")
  }

  override def stateToBytes(obj: SpiEventSourcedEntity.State): BytesPayload =
    serializer.toBytes(obj)

  override def stateFromBytes(pb: BytesPayload): SpiEventSourcedEntity.State =
    serializer.fromBytes(router.entityStateType, pb)
}
