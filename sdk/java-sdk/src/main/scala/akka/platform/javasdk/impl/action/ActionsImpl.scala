/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.impl.action

import java.util.Optional

import scala.concurrent.Future
import scala.util.control.NonFatal

import akka.NotUsed
import akka.actor.ActorSystem
import akka.platform.javasdk
import akka.platform.javasdk.Metadata
import akka.platform.javasdk.action.Action
import akka.platform.javasdk.action.ActionContext
import akka.platform.javasdk.action.ActionOptions
import akka.platform.javasdk.action.MessageContext
import akka.platform.javasdk.action.MessageEnvelope
import akka.platform.javasdk.consumer.Consumer
import akka.platform.javasdk.consumer.ConsumerContext
import akka.platform.javasdk.impl.ActionFactory
import akka.platform.javasdk.impl.ComponentOptions
import akka.platform.javasdk.impl.ErrorHandling
import akka.platform.javasdk.impl.ErrorHandling.BadRequestException
import akka.platform.javasdk.impl.MessageCodec
import akka.platform.javasdk.impl.MetadataImpl
import akka.platform.javasdk.impl.Service
import akka.platform.javasdk.impl._
import akka.platform.javasdk.impl.consumer
import akka.platform.javasdk.impl.consumer.ConsumerContextImpl
import akka.platform.javasdk.impl.consumer.ConsumerService
import akka.platform.javasdk.impl.telemetry.ActionCategory
import akka.platform.javasdk.impl.telemetry.ConsumerCategory
import akka.platform.javasdk.impl.telemetry.Instrumentation
import akka.platform.javasdk.impl.telemetry.Telemetry
import akka.platform.javasdk.impl.telemetry.TraceInstrumentation
import akka.platform.javasdk.impl.telemetry.TraceInstrumentation.TRACE_PARENT_KEY
import akka.platform.javasdk.impl.telemetry.TraceInstrumentation.TRACE_STATE_KEY
import akka.platform.javasdk.impl.timer.TimerSchedulerImpl
import akka.platform.javasdk.spi.TimerClient
import akka.platform.javasdk.timer.TimerScheduler
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import com.google.protobuf.Descriptors
import io.grpc.Status
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import kalix.protocol.action.ActionCommand
import kalix.protocol.action.ActionResponse
import kalix.protocol.action.Actions
import kalix.protocol.component
import kalix.protocol.component.Failure
import kalix.protocol.component.MetadataEntry
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC

final class ActionService(
    val factory: ActionFactory,
    override val descriptor: Descriptors.ServiceDescriptor,
    override val additionalDescriptors: Array[Descriptors.FileDescriptor],
    val messageCodec: MessageCodec,
    val actionOptions: Option[ActionOptions])
    extends Service {

  def this(
      factory: ActionFactory,
      descriptor: Descriptors.ServiceDescriptor,
      additionalDescriptors: Array[Descriptors.FileDescriptor],
      messageCodec: MessageCodec,
      actionOptions: ActionOptions) =
    this(factory, descriptor, additionalDescriptors, messageCodec, Some(actionOptions))

  @volatile var actionClass: Option[Class[_]] = None

  def createAction(context: ActionContext): ActionRouter[_] = {
    val handler = factory.create(context)
    actionClass = Some(handler.actionClass())
    handler
  }

  // use a logger specific to the service impl if possible (concrete action was successfully created at least once)
  def log: Logger = actionClass match {
    case Some(clazz) => LoggerFactory.getLogger(clazz)
    case None        => ActionsImpl.log
  }

  override def resolvedMethods: Option[Map[String, ResolvedServiceMethod[_, _]]] =
    factory match {
      case resolved: ResolvedEntityFactory => Some(resolved.resolvedMethods)
      case _                               => None
    }

  override def componentOptions: Option[ComponentOptions] = actionOptions

  override final val componentType = Actions.name
}

private[javasdk] object ActionsImpl {
  private[action] val log = LoggerFactory.getLogger(classOf[ActionsImpl])

  private def handleUnexpectedException(
      service: ActionService,
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
      service: ConsumerService,
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

private[akka] final class ActionsImpl(_system: ActorSystem, services: Map[String, Service], timerClient: TimerClient)
    extends Actions {

  import ActionsImpl._
  import _system.dispatcher
  implicit val system: ActorSystem = _system
  private val telemetry = Telemetry(system)
  lazy val telemetries: Map[String, Instrumentation] = services.values.map {
    case s: ActionService   => (s.serviceName, telemetry.traceInstrumentation(s.serviceName, ActionCategory))
    case s: ConsumerService => (s.serviceName, telemetry.traceInstrumentation(s.serviceName, ConsumerCategory))
  }.toMap

  private def effectToResponse(
      service: ActionService,
      command: ActionCommand,
      effect: Action.Effect[_],
      messageCodec: MessageCodec): Future[ActionResponse] = {
    import ActionEffectImpl._
    effect match {
      case ReplyEffect(message, metadata) =>
        val response =
          component.Reply(Some(messageCodec.encodeScala(message)), metadata.flatMap(MetadataImpl.toProtocol))
        Future.successful(ActionResponse(ActionResponse.Response.Reply(response)))
      case AsyncEffect(futureEffect) =>
        futureEffect
          .flatMap { effect => effectToResponse(service, command, effect, messageCodec) }
          .recover { case NonFatal(ex) =>
            handleUnexpectedException(service, command, ex)
          }
      case ErrorEffect(description, status) =>
        Future.successful(
          ActionResponse(
            ActionResponse.Response.Failure(
              Failure(description = description, grpcStatusCode = status.map(_.value()).getOrElse(0)))))
      case IgnoreEffect =>
        Future.successful(ActionResponse(ActionResponse.Response.Empty))
      case unknown =>
        throw new IllegalArgumentException(s"Unknown Action.Effect type ${unknown.getClass}")
    }
  }

  private def consumerEffectToResponse(
      service: ConsumerService,
      command: ActionCommand,
      effect: Consumer.Effect[_],
      messageCodec: MessageCodec): Future[ActionResponse] = {
    import akka.platform.javasdk.impl.consumer.ConsumerEffectImpl._
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
      case ErrorEffect(description, status) =>
        Future.successful(
          ActionResponse(
            ActionResponse.Response.Failure(
              Failure(description = description, grpcStatusCode = status.map(_.value()).getOrElse(0)))))
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
      case Some(service: ActionService) =>
        val span = telemetries(service.serviceName).buildSpan(service, in)

        span.foreach(s => MDC.put(Telemetry.TRACE_ID, s.getSpanContext.getTraceId))
        val fut =
          try {
            val messageContext =
              createMessageContext(in, service.messageCodec, span.map(_.getSpanContext), service.serviceName)
            val actionContext = createActionContext(service.serviceName)
            val decodedPayload = service.messageCodec.decodeMessage(
              in.payload.getOrElse(throw new IllegalArgumentException("No command payload")))
            val effect = service.factory
              .create(actionContext)
              .handleUnary(in.name, MessageEnvelope.of(decodedPayload, messageContext.metadata()), messageContext)
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

      case Some(service: ConsumerService) =>
        val span = telemetries(service.serviceName).buildSpan(service, in)

        span.foreach(s => MDC.put(Telemetry.TRACE_ID, s.getSpanContext.getTraceId))
        val fut =
          try {
            val messageContext =
              createConsumerMessageContext(in, service.messageCodec, span.map(_.getSpanContext), service.serviceName)
            val consumerContext = createConsumerContext(service.serviceName)
            val decodedPayload = service.messageCodec.decodeMessage(
              in.payload.getOrElse(throw new IllegalArgumentException("No command payload")))
            val effect = service.factory
              .create(consumerContext)
              .handleUnary(
                in.name,
                javasdk.consumer.MessageEnvelope.of(decodedPayload, messageContext.metadata()),
                messageContext)
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

  /**
   * Handle a streamed in command. The first message in will contain the request metadata, including the service name
   * and command name. It will not have an associated payload set. This will be followed by zero to many messages in
   * with a payload, but no service name or command name set. The semantics of stream closure in this protocol map 1:1
   * with the semantics of gRPC stream closure, that is, when the client closes the stream, the stream is considered
   * half closed, and the server should eventually, but not necessarily immediately, send a response message with a
   * status code and trailers. If however the server sends a response message before the client closes the stream, the
   * stream is completely closed, and the client should handle this and stop sending more messages. Either the client or
   * the server may cancel the stream at any time, cancellation is indicated through an HTTP2 stream RST message.
   */
  override def handleStreamedIn(in: Source[ActionCommand, NotUsed]): Future[ActionResponse] =
    in.prefixAndTail(1)
      .runWith(Sink.head)
      .flatMap {
        case (Nil, _) =>
          Future.successful(ActionResponse(ActionResponse.Response.Failure(Failure(
            0,
            "Kalix protocol failure: expected command message with service name and command name, but got empty stream"))))
        case (Seq(call), messages) =>
          services.get(call.serviceName) match {
            case Some(service: ActionService) =>
              try {
                val messageContext = createMessageContext(call, service.messageCodec, None, service.serviceName)
                val actionContext = createActionContext(service.serviceName)
                val effect = service.factory
                  .create(actionContext)
                  .handleStreamedIn(
                    call.name,
                    messages.map { message =>
                      val metadata = MetadataImpl.of(message.metadata.map(_.entries.toVector).getOrElse(Nil))
                      val decodedPayload = service.messageCodec.decodeMessage(
                        message.payload.getOrElse(throw new IllegalArgumentException("No command payload")))
                      MessageEnvelope.of(decodedPayload, metadata)
                    }.asJava,
                    messageContext)
                effectToResponse(service, call, effect, service.messageCodec)
              } catch {
                case NonFatal(ex) =>
                  // command handler threw an "unexpected" error
                  Future.successful(handleUnexpectedException(service, call, ex))
              }
            case Some(_: ConsumerService) =>
              Future.successful(
                ActionResponse(
                  ActionResponse.Response.Failure(Failure(0, "Streamed calls not supported: " + call.serviceName))))
            case _ =>
              Future.successful(
                ActionResponse(ActionResponse.Response.Failure(Failure(0, "Unknown service: " + call.serviceName))))
          }
      }

  /**
   * Handle a streamed out command. The input command will contain the service name, command name, request metadata and
   * the command payload. Zero or more replies may be sent, each containing either a direct reply, a forward or a
   * failure. The stream to the client will be closed when the this stream is closed, with the same status as this
   * stream is closed with. Either the client or the server may cancel the stream at any time, cancellation is indicated
   * through an HTTP2 stream RST message.
   */
  override def handleStreamedOut(in: ActionCommand): Source[ActionResponse, NotUsed] =
    services.get(in.serviceName) match {
      case Some(service: ActionService) =>
        try {
          val messageContext = createMessageContext(in, service.messageCodec, None, service.serviceName)
          val actionContext = createActionContext(service.serviceName)
          val decodedPayload = service.messageCodec.decodeMessage(
            in.payload.getOrElse(throw new IllegalArgumentException("No command payload")))
          service.factory
            .create(actionContext)
            .handleStreamedOut(in.name, MessageEnvelope.of(decodedPayload, messageContext.metadata()), messageContext)
            .asScala
            .mapAsync(1)(effect => effectToResponse(service, in, effect, service.messageCodec))
            .recover { case NonFatal(ex) =>
              // user stream failed with an "unexpected" error
              handleUnexpectedException(service, in, ex)
            }
            .async
        } catch {
          case NonFatal(ex) =>
            // command handler threw an "unexpected" error
            Source.single(handleUnexpectedException(service, in, ex))
        }
      case _ =>
        Source.single(ActionResponse(ActionResponse.Response.Failure(Failure(0, "Unknown service: " + in.serviceName))))
    }

  /**
   * Handle a full duplex streamed command. The first message in will contain the request metadata, including the
   * service name and command name. It will not have an associated payload set. This will be followed by zero to many
   * messages in with a payload, but no service name or command name set. Zero or more replies may be sent, each
   * containing either a direct reply, a forward or a failure. The semantics of stream closure in this protocol map 1:1
   * with the semantics of gRPC stream closure, that is, when the client closes the stream, the stream is considered
   * half closed, and the server should eventually, but not necessarily immediately, close the stream with a status code
   * and trailers. If however the server closes the stream with a status code and trailers, the stream is immediately
   * considered completely closed, and no further messages sent by the client will be handled by the server. Either the
   * client or the server may cancel the stream at any time, cancellation is indicated through an HTTP2 stream RST
   * message.
   */
  override def handleStreamed(in: Source[ActionCommand, NotUsed]): Source[ActionResponse, NotUsed] =
    in.prefixAndTail(1)
      .flatMapConcat {
        case (Nil, _) =>
          Source.single(ActionResponse(ActionResponse.Response.Failure(Failure(
            0,
            "Kalix protocol failure: expected command message with service name and command name, but got empty stream"))))
        case (Seq(call), messages) =>
          services.get(call.serviceName) match {
            case Some(service: ActionService) =>
              try {
                val messageContext = createMessageContext(call, service.messageCodec, None, service.serviceName)
                val actionContext = createActionContext(service.serviceName)
                service.factory
                  .create(actionContext)
                  .handleStreamed(
                    call.name,
                    messages.map { message =>
                      val metadata = MetadataImpl.of(message.metadata.map(_.entries.toVector).getOrElse(Nil))
                      val decodedPayload = service.messageCodec.decodeMessage(
                        message.payload.getOrElse(throw new IllegalArgumentException("No command payload")))
                      MessageEnvelope.of(decodedPayload, metadata)
                    }.asJava,
                    messageContext)
                  .asScala
                  .mapAsync(1)(effect => effectToResponse(service, call, effect, service.messageCodec))
                  .recover { case NonFatal(ex) =>
                    // user stream failed with an "unexpected" error
                    handleUnexpectedException(service, call, ex)
                  }
              } catch {
                case NonFatal(ex) =>
                  // command handler threw an "unexpected" error
                  ErrorHandling.withCorrelationId { correlationId =>
                    service.log.error(s"Failure during handling of command ${call.serviceName}.${call.name}", ex)
                    Source.single(protocolFailure(correlationId))
                  }
              }
            case _ =>
              Source.single(
                ActionResponse(ActionResponse.Response.Failure(Failure(0, "Unknown service: " + call.serviceName))))
          }
      }

  private def createMessageContext(
      in: ActionCommand,
      messageCodec: MessageCodec,
      spanContext: Option[SpanContext],
      serviceName: String): MessageContext = {
    val metadata = MetadataImpl.of(in.metadata.map(_.entries.toVector).getOrElse(Nil))
    val updatedMetadata = spanContext.map(metadataWithTracing(metadata, _)).getOrElse(metadata)
    new MessageContextImpl(updatedMetadata, messageCodec, system, timerClient, telemetries(serviceName))
  }

  private def createConsumerMessageContext(
      in: ActionCommand,
      messageCodec: MessageCodec,
      spanContext: Option[SpanContext],
      serviceName: String): javasdk.consumer.MessageContext = {
    val metadata = MetadataImpl.of(in.metadata.map(_.entries.toVector).getOrElse(Nil))
    val updatedMetadata = spanContext.map(metadataWithTracing(metadata, _)).getOrElse(metadata)
    new consumer.MessageContextImpl(updatedMetadata, messageCodec, system, timerClient, telemetries(serviceName))
  }

  private def createActionContext(serviceName: String): ActionContext = {
    new ActionContextImpl(system)
  }

  private def createConsumerContext(serviceName: String): ConsumerContext = {
    new ConsumerContextImpl(system)
  }

  private def metadataWithTracing(metadata: MetadataImpl, spanContext: SpanContext): Metadata = {
    // remove parent traceparent and tracestate from the metadata so they can be reinjected with current span context
    val l = metadata.entries.filter(m => m.key != TRACE_PARENT_KEY && m.key != TRACE_STATE_KEY).toBuffer

    W3CTraceContextPropagator
      .getInstance()
      .inject(io.opentelemetry.context.Context.current().`with`(Span.wrap(spanContext)), l, TraceInstrumentation.setter)

    if (log.isTraceEnabled)
      log.trace(
        "Updated metadata with trace context: [{}]",
        l.toList.filter(m => m.key == TRACE_PARENT_KEY || m.key == TRACE_STATE_KEY))
    MetadataImpl.of(l.toSeq)
  }

}

case class MessageEnvelopeImpl[T](payload: T, metadata: Metadata) extends MessageEnvelope[T]

/**
 * INTERNAL API
 */
class MessageContextImpl(
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

class ActionContextImpl(val system: ActorSystem) extends AbstractContext(system) with ActionContext {}
