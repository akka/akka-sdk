/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.impl.action

import scala.concurrent.Future
import scala.util.control.NonFatal

import akka.Done
import akka.NotUsed
import akka.actor.ActorSystem
import akka.platform.javasdk
import akka.platform.javasdk.Metadata
import akka.platform.javasdk.timedaction.CommandContext
import akka.platform.javasdk.timedaction.CommandEnvelope
import akka.platform.javasdk.timedaction.TimedAction
import akka.platform.javasdk.timedaction.TimedActionContext
import akka.platform.javasdk.timedaction.TimedActionOptions
import akka.platform.javasdk.consumer.Consumer
import akka.platform.javasdk.consumer.ConsumerContext
import akka.platform.javasdk.impl.ComponentOptions
import akka.platform.javasdk.impl.ErrorHandling
import akka.platform.javasdk.impl.ErrorHandling.BadRequestException
import akka.platform.javasdk.impl.MessageCodec
import akka.platform.javasdk.impl.MetadataImpl
import akka.platform.javasdk.impl.Service
import akka.platform.javasdk.impl.TimedActionFactory
import akka.platform.javasdk.impl._
import akka.platform.javasdk.impl.consumer
import akka.platform.javasdk.impl.consumer.ConsumerContextImpl
import akka.platform.javasdk.impl.consumer.ConsumerService
import akka.platform.javasdk.impl.telemetry.ActionCategory
import akka.platform.javasdk.impl.telemetry.ConsumerCategory
import akka.platform.javasdk.impl.telemetry.Instrumentation
import akka.platform.javasdk.impl.telemetry.Telemetry
import akka.platform.javasdk.impl.timedaction.TimedActionRouter
import akka.platform.javasdk.impl.timer.TimerSchedulerImpl
import akka.platform.javasdk.spi.TimerClient
import akka.platform.javasdk.timer.TimerScheduler
import akka.stream.scaladsl.Source
import com.google.protobuf.Descriptors
import io.grpc.Status
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.Tracer
import kalix.protocol.action.ActionCommand
import kalix.protocol.action.ActionResponse
import kalix.protocol.action.Actions
import kalix.protocol.component
import kalix.protocol.component.Failure
import kalix.protocol.component.MetadataEntry
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC

import scala.concurrent.ExecutionContext

final class ActionService(
    val factory: TimedActionFactory,
    override val descriptor: Descriptors.ServiceDescriptor,
    override val additionalDescriptors: Array[Descriptors.FileDescriptor],
    val messageCodec: MessageCodec,
    val actionOptions: Option[TimedActionOptions])
    extends Service {

  def this(
      factory: TimedActionFactory,
      descriptor: Descriptors.ServiceDescriptor,
      additionalDescriptors: Array[Descriptors.FileDescriptor],
      messageCodec: MessageCodec,
      actionOptions: TimedActionOptions) =
    this(factory, descriptor, additionalDescriptors, messageCodec, Some(actionOptions))

  @volatile var actionClass: Option[Class[_]] = None

  def createAction(context: TimedActionContext): TimedActionRouter[_] = {
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

private[akka] final class ActionsImpl(
    _system: ActorSystem,
    services: Map[String, Service],
    timerClient: TimerClient,
    sdkExecutionContext: ExecutionContext)
    extends Actions {

  import ActionsImpl._

  private implicit val executionContext: ExecutionContext = sdkExecutionContext
  implicit val system: ActorSystem = _system
  private val telemetry = Telemetry(system)
  lazy val telemetries: Map[String, Instrumentation] = services.values.map {
    case s: ActionService   => (s.serviceName, telemetry.traceInstrumentation(s.serviceName, ActionCategory))
    case s: ConsumerService => (s.serviceName, telemetry.traceInstrumentation(s.serviceName, ConsumerCategory))
  }.toMap

  private def effectToResponse(
      service: ActionService,
      command: ActionCommand,
      effect: TimedAction.Effect,
      messageCodec: MessageCodec): Future[ActionResponse] = {
    import akka.platform.javasdk.impl.timedaction.TimedActionEffectImpl._
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
      service: ConsumerService,
      command: ActionCommand,
      effect: Consumer.Effect,
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

  private def createMessageContext(
      in: ActionCommand,
      messageCodec: MessageCodec,
      spanContext: Option[SpanContext],
      serviceName: String): CommandContext = {
    val metadata = MetadataImpl.of(in.metadata.map(_.entries.toVector).getOrElse(Nil))
    val updatedMetadata = spanContext.map(metadata.withTracing).getOrElse(metadata)
    new CommandContextImpl(updatedMetadata, messageCodec, system, timerClient, telemetries(serviceName))
  }

  private def createConsumerMessageContext(
      in: ActionCommand,
      messageCodec: MessageCodec,
      spanContext: Option[SpanContext],
      serviceName: String): javasdk.consumer.MessageContext = {
    val metadata = MetadataImpl.of(in.metadata.map(_.entries.toVector).getOrElse(Nil))
    val updatedMetadata = spanContext.map(metadata.withTracing).getOrElse(metadata)
    new consumer.MessageContextImpl(updatedMetadata, messageCodec, timerClient, telemetries(serviceName))
  }

  private def createActionContext(serviceName: String): TimedActionContext = {
    new TimedActionContextImpl(system)
  }

  private def createConsumerContext(serviceName: String): ConsumerContext = {
    new ConsumerContextImpl(system)
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
    instrumentation: Instrumentation)
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

  override def getTracer: Tracer =
    instrumentation.getTracer

}

class TimedActionContextImpl(val system: ActorSystem) extends AbstractContext with TimedActionContext {}
