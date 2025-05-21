/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import scala.concurrent.Future
import scala.util.control.NonFatal
import akka.annotation.InternalApi
import akka.javasdk.Metadata
import akka.javasdk.Tracing
import akka.javasdk.agent.Agent
import akka.javasdk.agent.AgentContext
import akka.javasdk.agent.AiMessage
import akka.javasdk.agent.CoreMemory
import akka.javasdk.agent.CoreMemory.ConversationHistory
import akka.javasdk.agent.ModelProvider
import akka.javasdk.agent.UserMessage
import akka.javasdk.impl.AbstractContext
import akka.javasdk.impl.ComponentDescriptor
import akka.javasdk.impl.ComponentType
import akka.javasdk.impl.ErrorHandling.BadRequestException
import akka.javasdk.impl.HandlerNotFoundException
import akka.javasdk.impl.MetadataImpl
import akka.javasdk.impl.agent.AgentEffectImpl.ConstantSystemMessage
import akka.javasdk.impl.agent.AgentEffectImpl.NoPrimaryEffect
import akka.javasdk.impl.agent.AgentEffectImpl.RequestModel
import akka.javasdk.impl.agent.AgentEffectImpl.TemplateSystemMessage
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
import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import org.slf4j.LoggerFactory
import org.slf4j.MDC

/**
 * INTERNAL API
 */
@InternalApi
private[impl] object AgentImpl {
  private val log = LoggerFactory.getLogger(classOf[AgentImpl[_]])

  private class AgentContextImpl(
      override val sessionId: String,
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
    sessionId: String,
    val factory: AgentContext => A,
    tracerFactory: () => Tracer,
    serializer: JsonSerializer,
    componentDescriptor: ComponentDescriptor,
    regionInfo: RegionInfo,
    promptTemplateClient: PromptTemplateClient,
    coreMemoryClient: CoreMemory,
    config: Config)
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

      val additionalContext = toSpiContextMessages(coreMemoryClient.getFullHistory(sessionId))
      val spiEffect =
        commandEffect.primaryEffect match {
          case req: RequestModel =>
            val systemMessage = req.systemMessage match {
              case ConstantSystemMessage(message) => message
              case template: TemplateSystemMessage =>
                promptTemplateClient.getPromptTemplate(template.templateId).formatted(template.args)
            }
            val spiModelProvider = toSpiModelProvider(req.modelProvider)
            val metadata = MetadataImpl.toSpi(req.replyMetadata)

            // FIXME refactor to persist both user and ai message on transform method
            // save user message to conversation history right before returning effect
            coreMemoryClient.addUserMessage(componentId, sessionId, req.userMessage)
            new SpiAgent.RequestModelEffect(
              spiModelProvider,
              systemMessage,
              req.userMessage,
              additionalContext,
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

  private def toSpiContextMessages(conversationHistory: ConversationHistory): Vector[SpiAgent.ContextMessage] = {
    import scala.jdk.CollectionConverters._

    conversationHistory
      .messages()
      .asScala
      .flatMap {
        case m if m.isInstanceOf[AiMessage] =>
          Some(new SpiAgent.ContextMessage.AiMessage(m.asInstanceOf[AiMessage].getText))
        case m if m.isInstanceOf[UserMessage] =>
          Some(new SpiAgent.ContextMessage.UserMessage(m.asInstanceOf[UserMessage].getText))
        case m =>
          log.warn("Unsupported message type [{}], ignoring", m.getClass.getName)
          None
      }
      .toVector
  }

  private def toSpiModelProvider(modelProvider: ModelProvider): SpiAgent.ModelProvider = {
    modelProvider match {
      case p: ModelProvider.FromConfig =>
        toSpiModelProvider(modelProviderFromConfig(p.configPath()))
      case p: ModelProvider.Anthropic =>
        new SpiAgent.ModelProvider.Anthropic(
          apiKey = p.apiKey,
          modelName = p.modelName,
          baseUrl = p.baseUrl,
          temperature = p.temperature,
          topP = p.topP,
          topK = p.topK,
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

  private def modelProviderFromConfig(configPath: String): ModelProvider = {
    val actualPath =
      if (configPath == "")
        config.getString("akka.javasdk.agent.model-provider")
      else
        configPath

    if (actualPath == "")
      throw new IllegalArgumentException(
        s"You must define model provider configuration in [akka.javasdk.agent.model-provider]")

    val resolvedConfigPath =
      if (config.hasPath(actualPath))
        actualPath
      else if (!actualPath.contains('.') && config.hasPath("akka.javasdk.agent." + actualPath))
        "akka.javasdk.agent." + actualPath
      else
        throw new IllegalArgumentException(s"Undefined model provider configuration [$actualPath]")

    try {
      log.debug("Model provider from config [{}]", resolvedConfigPath)
      val providerConfig = config.getConfig(resolvedConfigPath)
      providerConfig.getString("provider") match {
        case "openai"    => ModelProvider.OpenAi.fromConfig(providerConfig)
        case "anthropic" => ModelProvider.OpenAi.fromConfig(providerConfig)
        case other =>
          throw new IllegalArgumentException(s"Unknown model provider [$other] in config [$resolvedConfigPath]")
      }
    } catch {
      case exc: ConfigException =>
        log.error("Invalid model provider configuration at [{}] for agent [{}].", resolvedConfigPath, componentId, exc)
        throw exc
    }
  }

  override def transformResponse(modelResponse: String, responseType: Class[_]): BytesPayload = {
    // FIXME we can persist both user and ai messages here if we refactor this to receive the complete builder back
    coreMemoryClient.addAiMessage(sessionId, modelResponse)
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
