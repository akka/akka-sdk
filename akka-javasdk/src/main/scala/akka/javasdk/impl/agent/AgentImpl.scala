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
import akka.javasdk.agent.Agent
import akka.javasdk.agent.AgentContext
import akka.javasdk.agent.ModelProvider
import akka.javasdk.impl.AbstractContext
import akka.javasdk.impl.ComponentDescriptor
import akka.javasdk.impl.ComponentType
import akka.javasdk.impl.ErrorHandling.BadRequestException
import akka.javasdk.impl.HandlerNotFoundException
import akka.javasdk.impl.MetadataImpl
import akka.javasdk.impl.agent.AgentEffectImpl.NoPrimaryEffect
import akka.javasdk.impl.agent.AgentEffectImpl.RequestModel
import akka.javasdk.impl.effect.ErrorReplyImpl
import akka.javasdk.impl.effect.MessageReplyImpl
import akka.javasdk.impl.effect.NoSecondaryEffectImpl
import akka.javasdk.impl.serialization.JsonSerializer
import akka.javasdk.impl.telemetry.AgentCategory
import akka.javasdk.impl.telemetry.SpanTracingImpl
import akka.javasdk.impl.telemetry.Telemetry
import akka.javasdk.impl.telemetry.TraceInstrumentation
import akka.runtime.sdk.spi.BytesPayload
import akka.runtime.sdk.spi.RegionInfo
import akka.runtime.sdk.spi.SpiAgent
import akka.runtime.sdk.spi.SpiMetadata
import akka.util.ByteString
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import org.slf4j.MDC

/**
 * INTERNAL API
 */
@InternalApi
private[impl] object AgentImpl {

  private class AgentContextImpl(
      override val sessionId: Optional[String],
      override val selfRegion: String,
      override val metadata: Metadata,
      span: Option[Span],
      tracerFactory: () => Tracer)
      extends AbstractContext
      with AgentContext {
    override def tracing(): Tracing = new SpanTracingImpl(span, tracerFactory)
  }

}

/**
 * INTERNAL API
 */
@InternalApi
private[impl] final class AgentImpl[A <: Agent](
    componentId: String,
    sessionId: Optional[String],
    val factory: AgentContext => A,
    tracerFactory: () => Tracer,
    serializer: JsonSerializer,
    componentDescriptor: ComponentDescriptor,
    regionInfo: RegionInfo)
    extends SpiAgent {
  import AgentImpl._

  private val traceInstrumentation = new TraceInstrumentation(componentId, AgentCategory, tracerFactory)

  private val router: ReflectiveAgentRouter = {
    val agentContext = new AgentContextImpl(sessionId, regionInfo.selfRegion, Metadata.EMPTY, None, tracerFactory)
    new ReflectiveAgentRouter(factory(agentContext), componentDescriptor.methodInvokers, serializer)
  }

  override def handleCommand(command: SpiAgent.Command): Future[SpiAgent.Effect] = {

    val span: Option[Span] =
      traceInstrumentation.buildSpan(ComponentType.Agent, componentId, None, command.metadata)
    span.foreach(s => MDC.put(Telemetry.TRACE_ID, s.getSpanContext.getTraceId))
    // smuggling 0 arity method called from component client through here
    val cmdPayload = command.payload.getOrElse(BytesPayload.empty)
    val metadata: Metadata = MetadataImpl.of(command.metadata)
    val agentContext = new AgentContextImpl(sessionId, regionInfo.selfRegion, metadata, span, tracerFactory)

    try {
      val commandEffect = router
        .handleCommand(command.name, cmdPayload, agentContext)
        .asInstanceOf[AgentEffectImpl[AnyRef]] // FIXME improve?

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

      val spiEffect =
        commandEffect.primaryEffect match {
          case req: RequestModel =>
            val spiModelProvider = toSpiModelProvider(req.modelProvider)
            val metadata = MetadataImpl.toSpi(req.replyMetadata)
            new SpiAgent.RequestModelEffect(
              spiModelProvider,
              req.systemMessage,
              req.userMessage,
              req.responseType,
              metadata)

          case NoPrimaryEffect =>
            errorOrReply match {
              case Left(err) =>
                new SpiAgent.ErrorEffect(err)
              case Right((reply, metadata)) =>
                new SpiAgent.ReplyEffect(reply, metadata)
            }
        }
      Future.successful(spiEffect)

    } catch {
      case e: HandlerNotFoundException =>
        throw AgentException(command.name, e.getMessage, Some(e))
      case BadRequestException(msg) =>
        Future.successful(new SpiAgent.ErrorEffect(error = new SpiAgent.Error(msg)))
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

  private def toSpiModelProvider(modelProvider: ModelProvider): SpiAgent.ModelProvider = {
    modelProvider match {
      case p: ModelProvider.FromConfig =>
        spiModelProviderFromConfig(p.configPath())
      case p: ModelProvider.Anthropic =>
        new SpiAgent.ModelProvider.Anthropic(
          apiKey = p.apiKey,
          modelName = p.modelName,
          baseUrl = p.baseUrl,
          temperature = p.temperature,
          topP = p.topP,
          maxTokens = p.maxTokens)
      case p: ModelProvider.OpenAi =>
        new SpiAgent.ModelProvider.OpenAi(
          apiKey = p.apiKey,
          modelName = p.modelName,
          baseUrl = p.baseUrl,
          temperature = p.temperature,
          topP = p.topP,
          maxTokens = p.maxTokens)
      case p: ModelProvider.Custom =>
        new SpiAgent.ModelProvider.Custom(() => p.createChatModel())
    }
  }

  private def spiModelProviderFromConfig(configPath: String): SpiAgent.ModelProvider = {
    // FIXME
    ???
  }

  override def transformResponse(modelResponse: String, responseType: Class[_]): BytesPayload = {
    if (responseType == classOf[String]) {
      serializer.toBytes(modelResponse)
    } else {
      // We might be able to bypass serialization roundtrip here, but might be good to catch invalid json
      // as early as possible.
      // The content type isn't used in this fromBytes.
      val obj = serializer.fromBytes(
        responseType,
        new BytesPayload(ByteString.fromString(modelResponse), JsonSerializer.JsonContentTypePrefix + "object"))
      serializer.toBytes(obj)
    }
  }
}
