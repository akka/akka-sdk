/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.timedaction

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal

import akka.actor.ActorSystem
import akka.annotation.InternalApi
import akka.javasdk.Metadata
import akka.javasdk.impl.ComponentDescriptor
import akka.javasdk.impl.ErrorHandling
import akka.javasdk.impl.JsonMessageCodec
import akka.javasdk.impl.MessageCodec
import akka.javasdk.impl.MetadataImpl
import akka.javasdk.impl.action.CommandContextImpl
import akka.javasdk.impl.telemetry.Telemetry
import akka.javasdk.impl.timedaction.TimedActionEffectImpl.AsyncEffect
import akka.javasdk.impl.timedaction.TimedActionEffectImpl.ErrorEffect
import akka.javasdk.impl.timedaction.TimedActionEffectImpl.ReplyEffect
import akka.javasdk.timedaction.CommandContext
import akka.javasdk.timedaction.CommandEnvelope
import akka.javasdk.timedaction.TimedAction
import akka.runtime.sdk.spi.SpiTimedAction
import akka.runtime.sdk.spi.SpiTimedAction.Command
import akka.runtime.sdk.spi.SpiTimedAction.Effect
import akka.runtime.sdk.spi.TimerClient
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC

/** EndMarker */
@InternalApi
private[impl] final class TimedActionImpl[TA <: TimedAction](
    val factory: () => TA,
    timedActionClass: Class[TA],
    _system: ActorSystem,
    timerClient: TimerClient,
    sdkExecutionContext: ExecutionContext,
    tracerFactory: () => Tracer,
    messageCodec: JsonMessageCodec)
    extends SpiTimedAction {

  private val log: Logger = LoggerFactory.getLogger(timedActionClass)

  private implicit val executionContext: ExecutionContext = sdkExecutionContext
  implicit val system: ActorSystem = _system

  private val componentDescriptor = ComponentDescriptor.descriptorFor(timedActionClass, messageCodec)

  // FIXME remove router altogether
  private def createRouter(): ReflectiveTimedActionRouter[TA] =
    new ReflectiveTimedActionRouter[TA](factory(), componentDescriptor.commandHandlers)

  override def handleCommand(command: Command): Future[Effect] = {
    val span: Option[Span] = None //FIXME add intrumentation

    span.foreach(s => MDC.put(Telemetry.TRACE_ID, s.getSpanContext.getTraceId))
    val fut =
      try {
        val messageContext =
          createMessageContext(command, messageCodec, span)
        val decodedPayload = messageCodec.decodeMessage(
          command.payload.getOrElse(throw new IllegalArgumentException("No command payload")))
        val metadata: Metadata =
          MetadataImpl.of(Nil) // FIXME MetadataImpl.of(command.metadata.map(_.entries.toVector).getOrElse(Nil))
        val effect = createRouter()
          .handleUnary(command.name, CommandEnvelope.of(decodedPayload, metadata), messageContext)
        toSpiEffect(command, effect)
      } catch {
        case NonFatal(ex) =>
          // command handler threw an "unexpected" error
          span.foreach(_.end())
          Future.successful(handleUnexpectedException(command, ex))
      } finally {
        MDC.remove(Telemetry.TRACE_ID)
      }
    fut.andThen { case _ =>
      span.foreach(_.end())
    }
  }

  private def createMessageContext(command: Command, messageCodec: MessageCodec, span: Option[Span]): CommandContext = {
    val metadata: MetadataImpl =
      MetadataImpl.of(Nil) // FIXME MetadataImpl.of(command.metadata.map(_.entries.toVector).getOrElse(Nil))
    val updatedMetadata = span.map(metadata.withTracing).getOrElse(metadata)
    new CommandContextImpl(updatedMetadata, messageCodec, system, timerClient, tracerFactory, span)
  }

  private def toSpiEffect(command: Command, effect: TimedAction.Effect): Future[Effect] = {
    effect match {
      case ReplyEffect(_) => //FIXME remove meta, not used in the reply
        Future.successful(new Effect(None))
      case AsyncEffect(futureEffect) =>
        futureEffect
          .flatMap { effect => toSpiEffect(command, effect) }
          .recover { case NonFatal(ex) =>
            handleUnexpectedException(command, ex)
          }
      case ErrorEffect(description) =>
        Future.successful(new Effect(Some(new SpiTimedAction.Error(description))))
      case unknown =>
        throw new IllegalArgumentException(s"Unknown TimedAction.Effect type ${unknown.getClass}")
    }
  }

  private def handleUnexpectedException(command: Command, ex: Throwable): Effect = {
    ex match {
      case _ =>
        ErrorHandling.withCorrelationId { correlationId =>
          log.error(
            s"Failure during handling command [${command.name}] from TimedAction component [${command.componentId}].",
            ex)
          protocolFailure(correlationId)
        }
    }
  }

  private def protocolFailure(correlationId: String): Effect = {
    new Effect(Some(new SpiTimedAction.Error(s"Unexpected error [$correlationId]")))
  }

}
