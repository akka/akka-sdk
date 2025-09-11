/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.timedaction

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal

import akka.actor.ActorSystem
import akka.annotation.InternalApi
import akka.javasdk.Metadata
import akka.javasdk.Tracing
import akka.javasdk.impl.AbstractContext
import akka.javasdk.impl.ComponentDescriptor
import akka.javasdk.impl.ErrorHandling
import akka.javasdk.impl.MetadataImpl
import akka.javasdk.impl.serialization.JsonSerializer
import akka.javasdk.impl.telemetry.SpanTracingImpl
import akka.javasdk.impl.telemetry.Telemetry
import akka.javasdk.impl.timedaction.TimedActionEffectImpl.AsyncEffect
import akka.javasdk.impl.timedaction.TimedActionEffectImpl.ErrorEffect
import akka.javasdk.impl.timedaction.TimedActionEffectImpl.SuccessEffect
import akka.javasdk.impl.timer.TimerSchedulerImpl
import akka.javasdk.timedaction.CommandContext
import akka.javasdk.timedaction.CommandEnvelope
import akka.javasdk.timedaction.TimedAction
import akka.javasdk.timer.TimerScheduler
import akka.runtime.sdk.spi.BytesPayload
import akka.runtime.sdk.spi.RegionInfo
import akka.runtime.sdk.spi.SpiTimedAction
import akka.runtime.sdk.spi.SpiTimedAction.Command
import akka.runtime.sdk.spi.SpiTimedAction.Effect
import akka.runtime.sdk.spi.TimerClient
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.{ Context => OtelContext }
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC

object TimedActionImpl {

  /**
   * INTERNAL API
   */
  class CommandContextImpl(
      override val selfRegion: String,
      override val metadata: Metadata,
      timerClient: TimerClient,
      val telemetryContext: Option[OtelContext],
      tracerFactory: () => Tracer)
      extends AbstractContext
      with CommandContext {

    val timers: TimerScheduler = new TimerSchedulerImpl(timerClient, componentCallMetadata)

    override def componentCallMetadata: MetadataImpl = {
      telemetryContext.fold(MetadataImpl.Empty)(MetadataImpl.Empty.withTelemetryContext)
    }

    override def tracing(): Tracing = new SpanTracingImpl(telemetryContext, tracerFactory)
  }

  final case class CommandEnvelopeImpl[T](payload: T, metadata: Metadata) extends CommandEnvelope[T]
}

/** EndMarker */
@InternalApi
private[impl] final class TimedActionImpl[TA <: TimedAction](
    componentId: String,
    val factory: CommandContext => TA,
    timedActionClass: Class[TA],
    _system: ActorSystem,
    timerClient: TimerClient,
    sdkExecutionContext: ExecutionContext,
    tracerFactory: () => Tracer,
    jsonSerializer: JsonSerializer,
    regionInfo: RegionInfo,
    componentDescriptor: ComponentDescriptor)
    extends SpiTimedAction {
  import TimedActionImpl.CommandContextImpl

  private val log: Logger = LoggerFactory.getLogger(timedActionClass)

  private implicit val executionContext: ExecutionContext = sdkExecutionContext
  implicit val system: ActorSystem = _system

  private def createRouter(timedAction: TA): ReflectiveTimedActionRouter[TA] =
    new ReflectiveTimedActionRouter[TA](timedAction, componentDescriptor.methodInvokers, jsonSerializer)

  override def handleCommand(command: Command): Future[Effect] = {
    val metadata = MetadataImpl.of(command.metadata)
    val telemetryContext = Option(command.telemetryContext)
    val traceId = telemetryContext.flatMap { context =>
      Option(Span.fromContextOrNull(context)).map(_.getSpanContext.getTraceId)
    }

    traceId.foreach(id => MDC.put(Telemetry.TRACE_ID, id))
    try {
      val commandContext =
        new CommandContextImpl(regionInfo.selfRegion, metadata, timerClient, telemetryContext, tracerFactory)

      val timedAction = factory(commandContext)

      val payload: BytesPayload = command.payload.getOrElse(throw new IllegalArgumentException("No command payload"))
      val effect = createRouter(timedAction)
        .handleCommand(command.name, CommandEnvelope.of(payload, commandContext.metadata), commandContext)
      toSpiEffect(command, effect)
    } catch {
      case NonFatal(ex) =>
        // command handler threw an "unexpected" error, also covers HandlerNotFoundException
        Future.successful(handleUnexpectedException(command, ex))
    } finally {
      if (traceId.isDefined) MDC.remove(Telemetry.TRACE_ID)
    }
  }

  private def toSpiEffect(command: Command, effect: TimedAction.Effect): Future[Effect] = {
    effect match {
      case SuccessEffect =>
        Future.successful(SpiTimedAction.SuccessEffect)
      case AsyncEffect(futureEffect) =>
        futureEffect
          .flatMap { effect => toSpiEffect(command, effect) }
          .recover { case NonFatal(ex) =>
            handleUnexpectedException(command, ex)
          }
      case ErrorEffect(description) =>
        Future.successful(new SpiTimedAction.ErrorEffect(new SpiTimedAction.Error(description)))
      case unknown =>
        throw new IllegalArgumentException(s"Unknown TimedAction.Effect type ${unknown.getClass}")
    }
  }

  private def handleUnexpectedException(command: Command, ex: Throwable): Effect = {
    ErrorHandling.withCorrelationId { correlationId =>
      log.error(
        s"Failure during handling command [${command.name}] from TimedAction component [${timedActionClass.getSimpleName}].",
        ex)
      protocolFailure(correlationId)
    }
  }

  private def protocolFailure(correlationId: String): Effect = {
    new SpiTimedAction.ErrorEffect(new SpiTimedAction.Error(s"Unexpected error [$correlationId]"))
  }

}
