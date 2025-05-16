/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import java.util.Optional

import scala.concurrent.Future
import scala.util.control.NonFatal

import akka.annotation.InternalApi
import akka.javasdk.Metadata
import akka.javasdk.Tracing
import akka.javasdk.agent.ChatAgent
import akka.javasdk.agent.ChatAgentContext
import akka.javasdk.impl.AbstractContext
import akka.javasdk.impl.ComponentDescriptor
import akka.javasdk.impl.ComponentType
import akka.javasdk.impl.ErrorHandling.BadRequestException
import akka.javasdk.impl.HandlerNotFoundException
import akka.javasdk.impl.MetadataImpl
import akka.javasdk.impl.agent.ChatAgentEffectImpl.NoPrimaryEffect
import akka.javasdk.impl.agent.ChatAgentEffectImpl.RequestModel
import akka.javasdk.impl.agent.spi.SpiAgent
import akka.javasdk.impl.agent.spi.SpiChatAgent
import akka.javasdk.impl.effect.ErrorReplyImpl
import akka.javasdk.impl.effect.MessageReplyImpl
import akka.javasdk.impl.effect.NoSecondaryEffectImpl
import akka.javasdk.impl.serialization.JsonSerializer
import akka.javasdk.impl.telemetry.ChatAgentCategory
import akka.javasdk.impl.telemetry.SpanTracingImpl
import akka.javasdk.impl.telemetry.Telemetry
import akka.javasdk.impl.telemetry.TraceInstrumentation
import akka.runtime.sdk.spi.BytesPayload
import akka.runtime.sdk.spi.RegionInfo
import akka.runtime.sdk.spi.SpiMetadata
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import org.slf4j.MDC

/**
 * INTERNAL API
 */
@InternalApi
private[impl] object ChatAgentImpl {

  private class ChatAgentContextImpl(
      override val sessionId: Optional[String],
      override val selfRegion: String,
      override val metadata: Metadata,
      span: Option[Span],
      tracerFactory: () => Tracer)
      extends AbstractContext
      with ChatAgentContext {
    override def tracing(): Tracing = new SpanTracingImpl(span, tracerFactory)
  }

}

/**
 * INTERNAL API
 */
@InternalApi
private[impl] final class ChatAgentImpl[A <: ChatAgent](
    componentId: String,
    sessionId: Optional[String],
    val factory: ChatAgentContext => A,
    tracerFactory: () => Tracer,
    serializer: JsonSerializer,
    componentDescriptor: ComponentDescriptor,
    regionInfo: RegionInfo)
    extends SpiChatAgent {
  import ChatAgentImpl._

  private val traceInstrumentation = new TraceInstrumentation(componentId, ChatAgentCategory, tracerFactory)

  private val router: ReflectiveChatAgentRouter = {
    val agentContext = new ChatAgentContextImpl(sessionId, regionInfo.selfRegion, Metadata.EMPTY, None, tracerFactory)
    new ReflectiveChatAgentRouter(factory(agentContext), componentDescriptor.methodInvokers, serializer)
  }

  override def handleCommand(command: SpiAgent.Command): Future[SpiChatAgent.Effect] = {

    val span: Option[Span] =
      traceInstrumentation.buildSpan(ComponentType.ChatAgent, componentId, None, command.metadata)
    span.foreach(s => MDC.put(Telemetry.TRACE_ID, s.getSpanContext.getTraceId))
    // smuggling 0 arity method called from component client through here
    val cmdPayload = command.payload.getOrElse(BytesPayload.empty)
    val metadata: Metadata = MetadataImpl.of(command.metadata)
    val agentContext = new ChatAgentContextImpl(sessionId, regionInfo.selfRegion, metadata, span, tracerFactory)

    try {
      val commandEffect = router
        .handleCommand(command.name, cmdPayload, agentContext)
        .asInstanceOf[ChatAgentEffectImpl[AnyRef]] // FIXME improve?

      def errorOrReply: Either[SpiAgent.Error, (BytesPayload, SpiMetadata)] = {
        commandEffect.secondaryEffect match {
          case ErrorReplyImpl(description) =>
            Left(new SpiAgent.Error(description))
          case MessageReplyImpl(message, m) =>
            val replyPayload = serializer.toBytes(message)
            val metadata = MetadataImpl.toSpi(m)
            Right(replyPayload -> metadata)
          case NoSecondaryEffectImpl =>
            throw new IllegalStateException("Expected reply or error")
        }
      }

      commandEffect.primaryEffect match {
        case req: RequestModel =>
          val metadata = MetadataImpl.toSpi(req.replyMetadata)
          Future.successful(new SpiChatAgent.RequestModelEffect(req.systemMessage, req.userMessage, metadata))

        case NoPrimaryEffect =>
          errorOrReply match {
            case Left(err) =>
              Future.successful(new SpiChatAgent.ErrorEffect(err))
            case Right((reply, metadata)) =>
              Future.successful(new SpiChatAgent.ReplyEffect(reply, metadata))
          }
      }

    } catch {
      case e: HandlerNotFoundException =>
        throw AgentException(command.name, e.getMessage, Some(e))
      case BadRequestException(msg) =>
        Future.successful(new SpiChatAgent.ErrorEffect(error = new SpiAgent.Error(msg)))
      case e: AgentException => throw e
      case NonFatal(error) =>
        throw AgentException(command.name, s"Unexpected failure: $error", Some(error))
    } finally {
      span.foreach { s =>
        MDC.remove(Telemetry.TRACE_ID)
        s.end()
      }
    }

  }

}
