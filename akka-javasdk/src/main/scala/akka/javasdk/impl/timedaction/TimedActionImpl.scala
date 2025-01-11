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
import akka.javasdk.Tracing
import akka.javasdk.impl.AbstractContext
import akka.javasdk.impl.ComponentDescriptor
import akka.javasdk.impl.ComponentType
import akka.javasdk.impl.ErrorHandling
import akka.javasdk.impl.MetadataImpl
import akka.javasdk.impl.serialization.JsonSerializer
import akka.javasdk.impl.telemetry.SpanTracingImpl
import akka.javasdk.impl.telemetry.Telemetry
import akka.javasdk.impl.telemetry.TimedActionCategory
import akka.javasdk.impl.telemetry.TraceInstrumentation
import akka.javasdk.impl.timedaction.TimedActionEffectImpl.AsyncEffect
import akka.javasdk.impl.timedaction.TimedActionEffectImpl.ErrorEffect
import akka.javasdk.impl.timedaction.TimedActionEffectImpl.SuccessEffect
import akka.javasdk.impl.timer.TimerSchedulerImpl
import akka.javasdk.timedaction.CommandContext
import akka.javasdk.timedaction.CommandEnvelope
import akka.javasdk.timedaction.TimedAction
import akka.javasdk.timer.TimerScheduler
import akka.runtime.sdk.spi.BytesPayload
import akka.runtime.sdk.spi.SpiMetadataEntry
import akka.runtime.sdk.spi.SpiTimedAction
import akka.runtime.sdk.spi.SpiTimedAction.Command
import akka.runtime.sdk.spi.SpiTimedAction.Effect
import akka.runtime.sdk.spi.TimerClient
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC

object TimedActionImpl {

  /**
   * INTERNAL API
   */
  class CommandContextImpl(
      override val metadata: Metadata,
      timerClient: TimerClient,
      tracerFactory: () => Tracer,
      span: Option[Span])
      extends AbstractContext
      with CommandContext {

    val timers: TimerScheduler = new TimerSchedulerImpl(timerClient, componentCallMetadata)

    override def componentCallMetadata: MetadataImpl = {
      if (metadata.has(Telemetry.TRACE_PARENT_KEY)) {
        MetadataImpl.of(
          List(new SpiMetadataEntry(Telemetry.TRACE_PARENT_KEY, metadata.get(Telemetry.TRACE_PARENT_KEY).get())))
      } else {
        MetadataImpl.Empty
      }
    }

    override def tracing(): Tracing = new SpanTracingImpl(span, tracerFactory)
  }

  final case class CommandEnvelopeImpl[T](payload: T, metadata: Metadata) extends CommandEnvelope[T]
}

/** EndMarker */
@InternalApi
private[impl] final class TimedActionImpl[TA <: TimedAction](
    componentId: String,
    val factory: () => TA,
    timedActionClass: Class[TA],
    _system: ActorSystem,
    timerClient: TimerClient,
    sdkExecutionContext: ExecutionContext,
    tracerFactory: () => Tracer,
    jsonSerializer: JsonSerializer,
    componentDescriptor: ComponentDescriptor)
    extends SpiTimedAction {
  import TimedActionImpl.CommandContextImpl

  private val log: Logger = LoggerFactory.getLogger(timedActionClass)

  private implicit val executionContext: ExecutionContext = sdkExecutionContext
  implicit val system: ActorSystem = _system
  private val traceInstrumentation = new TraceInstrumentation(componentId, TimedActionCategory, tracerFactory)

  private def createRouter(): ReflectiveTimedActionRouter[TA] =
    new ReflectiveTimedActionRouter[TA](factory(), componentDescriptor.methodInvokers, jsonSerializer)

  override def handleCommand(command: Command): Future[Effect] = {
    val metadata = MetadataImpl.of(command.metadata)

    // FIXME would be good if we could record the chosen method in the span
    val span: Option[Span] =
      traceInstrumentation.buildSpan(ComponentType.TimedAction, componentId, metadata.subjectScala, command.metadata)

    span.foreach(s => MDC.put(Telemetry.TRACE_ID, s.getSpanContext.getTraceId))
    val fut =
      try {
        val updatedMetadata = span.map(metadata.withTracing).getOrElse(metadata)
        val commandContext = new CommandContextImpl(updatedMetadata, timerClient, tracerFactory, span)

        val payload: BytesPayload = command.payload.getOrElse(throw new IllegalArgumentException("No command payload"))
        val effect = createRouter()
          .handleCommand(command.name, CommandEnvelope.of(payload, commandContext.metadata), commandContext)
        toSpiEffect(command, effect)
      } catch {
        case NonFatal(ex) =>
          // command handler threw an "unexpected" error, also covers HandlerNotFoundException
          Future.successful(handleUnexpectedException(command, ex))
      } finally {
        MDC.remove(Telemetry.TRACE_ID)
      }
    fut.andThen { case _ =>
      span.foreach(_.end())
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
