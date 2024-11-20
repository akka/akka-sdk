/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.action

import akka.Done
import akka.NotUsed
import akka.actor.ActorSystem
import akka.annotation.InternalApi
import akka.javasdk.Metadata
import akka.javasdk.Tracing
import akka.javasdk.consumer.Consumer
import akka.javasdk.consumer.MessageContext
import akka.javasdk.consumer.MessageEnvelope
import akka.javasdk.impl.AbstractContext
import akka.javasdk.impl.ErrorHandling
import akka.javasdk.impl.ErrorHandling.BadRequestException
import akka.javasdk.impl.MessageCodec
import akka.javasdk.impl.MetadataImpl
import akka.javasdk.impl.Service
import akka.javasdk.impl.consumer.ConsumerService
import akka.javasdk.impl.consumer.MessageContextImpl
import akka.javasdk.impl.telemetry.ActionCategory
import akka.javasdk.impl.telemetry.ConsumerCategory
import akka.javasdk.impl.telemetry.Telemetry
import akka.javasdk.impl.telemetry.TraceInstrumentation
import akka.javasdk.impl.telemetry.SpanTracingImpl
import akka.javasdk.impl.timedaction.TimedActionService
import akka.javasdk.impl.timer.TimerSchedulerImpl
import akka.javasdk.timedaction.CommandContext
import akka.javasdk.timedaction.CommandEnvelope
import akka.javasdk.timedaction.TimedAction
import akka.javasdk.timer.TimerScheduler
import akka.runtime.sdk.spi.TimerClient
import akka.stream.scaladsl.Source
import io.grpc.Status
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import kalix.protocol.action.ActionCommand
import kalix.protocol.action.ActionResponse
import kalix.protocol.action.Actions
import kalix.protocol.component
import kalix.protocol.component.Failure
import kalix.protocol.component.MetadataEntry
import org.slf4j.MDC

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] object ActionsImpl {

  private def handleUnexpectedException(
      service: TimedActionService[_],
      command: ActionCommand,
      ex: Throwable): ActionResponse = {
    ex match {
      case badReqEx: BadRequestException => handleBadRequest(badReqEx.getMessage)
      case _ =>
        ErrorHandling.withCorrelationId { correlationId =>
          service.log.error(s"Failure during handling of command ${command.serviceName}.${command.name}", ex)
          protocolFailure(correlationId)
        }
    }
  }

  private def handleUnexpectedExceptionInConsumer(
      service: ConsumerService[_],
      command: ActionCommand,
      ex: Throwable): ActionResponse = {
    ex match {
      case badReqEx: BadRequestException => handleBadRequest(badReqEx.getMessage)
      case _ =>
        ErrorHandling.withCorrelationId { correlationId =>
          service.log.error(s"Failure during handling of command ${command.serviceName}.${command.name}", ex)
          protocolFailure(correlationId)
        }
    }
  }

  private def handleBadRequest(description: String): ActionResponse =
    ActionResponse(ActionResponse.Response.Failure(Failure(0, description, Status.Code.INVALID_ARGUMENT.value())))

  private def protocolFailure(correlationId: String): ActionResponse = {
    ActionResponse(ActionResponse.Response.Failure(Failure(0, s"Unexpected error [$correlationId]")))
  }

}

private[akka] final class ActionsImpl(
    _system: ActorSystem,
    services: Map[String, Service],
    timerClient: TimerClient,
    sdkExecutionContext: ExecutionContext,
    tracerFactory: () => Tracer)
    extends Actions {

  import ActionsImpl._

  private implicit val executionContext: ExecutionContext = sdkExecutionContext
  implicit val system: ActorSystem = _system

  private val telemetries: Map[String, TraceInstrumentation] =
    services.values.map {
      case s: TimedActionService[_] =>
        (s.componentId, new TraceInstrumentation(s.componentId, ActionCategory, tracerFactory))
      case s: ConsumerService[_] =>
        (s.componentId, new TraceInstrumentation(s.componentId, ConsumerCategory, tracerFactory))
    }.toMap

  private def effectToResponse(
      service: TimedActionService[_],
      command: ActionCommand,
      effect: TimedAction.Effect,
      messageCodec: MessageCodec): Future[ActionResponse] = {
    import akka.javasdk.impl.timedaction.TimedActionEffectImpl._
    effect match {
      case ReplyEffect(metadata) =>
        val response =
          component.Reply(Some(messageCodec.encodeScala(Done)), metadata.flatMap(MetadataImpl.toProtocol))
        Future.successful(ActionResponse(ActionResponse.Response.Reply(response)))
      case AsyncEffect(futureEffect) =>
        futureEffect
          .flatMap { effect => effectToResponse(service, command, effect, messageCodec) }
          .recover { case NonFatal(ex) =>
            handleUnexpectedException(service, command, ex)
          }
      case ErrorEffect(description) =>
        Future.successful(ActionResponse(ActionResponse.Response.Failure(Failure(description = description))))
      case unknown =>
        throw new IllegalArgumentException(s"Unknown Action.Effect type ${unknown.getClass}")
    }
  }

  private def consumerEffectToResponse(
      service: ConsumerService[_],
      command: ActionCommand,
      effect: Consumer.Effect,
      messageCodec: MessageCodec): Future[ActionResponse] = {
    import akka.javasdk.impl.consumer.ConsumerEffectImpl._
    effect match {
      case ReplyEffect(message, metadata) =>
        val response =
          component.Reply(Some(messageCodec.encodeScala(message)), metadata.flatMap(MetadataImpl.toProtocol))
        Future.successful(ActionResponse(ActionResponse.Response.Reply(response)))
      case AsyncEffect(futureEffect) =>
        futureEffect
          .flatMap { effect => consumerEffectToResponse(service, command, effect, messageCodec) }
          .recover { case NonFatal(ex) =>
            handleUnexpectedExceptionInConsumer(service, command, ex)
          }
      case IgnoreEffect =>
        Future.successful(ActionResponse(ActionResponse.Response.Empty))
      case unknown =>
        throw new IllegalArgumentException(s"Unknown Action.Effect type ${unknown.getClass}")
    }
  }

  /**
   * Handle a unary command. The input command will contain the service name, command name, request metadata and the
   * command payload. The reply may contain a direct reply, a forward or a failure, and it may contain many side
   * effects.
   */
  override def handleUnary(in: ActionCommand): Future[ActionResponse] =
    services.get(in.serviceName) match {
      case Some(service: TimedActionService[_]) =>
        val span = telemetries(service.componentId).buildSpan(service, in)

        span.foreach(s => MDC.put(Telemetry.TRACE_ID, s.getSpanContext.getTraceId))
        val fut =
          try {
            val messageContext =
              createMessageContext(in, service.messageCodec, span, service.componentId)
            val decodedPayload = service.messageCodec.decodeMessage(
              in.payload.getOrElse(throw new IllegalArgumentException("No command payload")))
            val effect = service
              .createRouter()
              .handleUnary(in.name, CommandEnvelope.of(decodedPayload, messageContext.metadata()), messageContext)
            effectToResponse(service, in, effect, service.messageCodec)
          } catch {
            case NonFatal(ex) =>
              // command handler threw an "unexpected" error
              span.foreach(_.end())
              Future.successful(handleUnexpectedException(service, in, ex))
          } finally {
            MDC.remove(Telemetry.TRACE_ID)
          }
        fut.andThen { case _ =>
          span.foreach(_.end())
        }

      case Some(service: ConsumerService[_]) =>
        val span = telemetries(service.componentId).buildSpan(service, in)

        span.foreach(s => MDC.put(Telemetry.TRACE_ID, s.getSpanContext.getTraceId))
        val fut =
          try {
            val messageContext =
              createConsumerMessageContext(in, service.messageCodec, span, service.componentId)
            val decodedPayload = service.messageCodec.decodeMessage(
              in.payload.getOrElse(throw new IllegalArgumentException("No command payload")))
            val effect = service
              .createRouter()
              .handleUnary(in.name, MessageEnvelope.of(decodedPayload, messageContext.metadata()), messageContext)
            consumerEffectToResponse(service, in, effect, service.messageCodec)
          } catch {
            case NonFatal(ex) =>
              // command handler threw an "unexpected" error
              span.foreach(_.end())
              Future.successful(handleUnexpectedExceptionInConsumer(service, in, ex))
          } finally {
            MDC.remove(Telemetry.TRACE_ID)
          }
        fut.andThen { case _ =>
          span.foreach(_.end())
        }
      case _ =>
        Future.successful(
          ActionResponse(ActionResponse.Response.Failure(Failure(0, "Unknown service: " + in.serviceName))))
    }

  private def createMessageContext(
      in: ActionCommand,
      messageCodec: MessageCodec,
      span: Option[Span],
      serviceName: String): CommandContext = {
    val metadata = MetadataImpl.of(in.metadata.map(_.entries.toVector).getOrElse(Nil))
    val updatedMetadata = span.map(metadata.withTracing).getOrElse(metadata)
    new CommandContextImpl(updatedMetadata, messageCodec, system, timerClient, tracerFactory, span)
  }

  private def createConsumerMessageContext(
      in: ActionCommand,
      messageCodec: MessageCodec,
      span: Option[Span],
      serviceName: String): MessageContext = {
    val metadata = MetadataImpl.of(in.metadata.map(_.entries.toVector).getOrElse(Nil))
    val updatedMetadata = span.map(metadata.withTracing).getOrElse(metadata)
    new MessageContextImpl(updatedMetadata, messageCodec, timerClient, tracerFactory, span)
  }

  override def handleStreamedIn(in: Source[ActionCommand, NotUsed]): Future[ActionResponse] = {
    throw new UnsupportedOperationException("Stream in calls are not supported")
  }

  override def handleStreamedOut(in: ActionCommand): Source[ActionResponse, NotUsed] = {
    throw new UnsupportedOperationException("Stream out not supported")
  }

  override def handleStreamed(in: Source[ActionCommand, NotUsed]): Source[ActionResponse, NotUsed] = {
    throw new UnsupportedOperationException("Stream in calls are not supported")
  }
}

case class CommandEnvelopeImpl[T](payload: T, metadata: Metadata) extends CommandEnvelope[T]

/**
 * INTERNAL API
 */
class CommandContextImpl(
    override val metadata: Metadata,
    val messageCodec: MessageCodec,
    val system: ActorSystem,
    timerClient: TimerClient,
    tracerFactory: () => Tracer,
    span: Option[Span])
    extends AbstractContext
    with CommandContext {

  val timers: TimerScheduler = new TimerSchedulerImpl(messageCodec, timerClient, componentCallMetadata)

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

  override def tracing(): Tracing = new SpanTracingImpl(span, tracerFactory)
}
