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
import akka.javasdk.agent.JsonParsingException
import akka.javasdk.agent.SessionMessage.AiMessage
import akka.javasdk.agent.SessionMessage.UserMessage
import akka.javasdk.agent.MemoryProvider
import akka.javasdk.agent.ModelProvider
import akka.javasdk.agent.SessionMemory
import akka.javasdk.client.ComponentClient
import akka.javasdk.impl.AbstractContext
import akka.javasdk.impl.ComponentDescriptor
import akka.javasdk.impl.ComponentType
import akka.javasdk.impl.ErrorHandling.BadRequestException
import akka.javasdk.impl.HandlerNotFoundException
import akka.javasdk.impl.MetadataImpl
import akka.javasdk.impl.agent.BaseAgentEffectBuilder.ConstantSystemMessage
import akka.javasdk.impl.agent.BaseAgentEffectBuilder.NoPrimaryEffect
import akka.javasdk.impl.agent.BaseAgentEffectBuilder.RequestModel
import akka.javasdk.impl.agent.BaseAgentEffectBuilder.TemplateSystemMessage
import SessionMemoryClient.MemorySettings
import akka.javasdk.agent.SessionHistory
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
    componentClient: ComponentClient,
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
      val commandEffect = router.handleCommand(command.name, cmdPayload, agentContext)

      def primaryEffect =
        commandEffect match {
          case e: AgentEffectImpl       => e.primaryEffect
          case e: AgentStreamEffectImpl => e.primaryEffect
        }

      def secondaryEffect =
        commandEffect match {
          case e: AgentEffectImpl       => e.secondaryEffect
          case e: AgentStreamEffectImpl => e.secondaryEffect
        }

      def errorOrReply: Either[SpiAgent.Error, (BytesPayload, SpiMetadata)] = {
        secondaryEffect match {
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
        primaryEffect match {
          case req: RequestModel =>
            val systemMessage = req.systemMessage match {
              case ConstantSystemMessage(message) => message
              case template: TemplateSystemMessage =>
                promptTemplateClient.getPromptTemplate(template.templateId).formatted(template.args)
            }
            val spiModelProvider = toSpiModelProvider(req.modelProvider)
            val metadata = MetadataImpl.toSpi(req.replyMetadata)
            val sessionMemoryClient = deriveMemoryClient(req.memoryProvider)
            val additionalContext = toSpiContextMessages(sessionMemoryClient.getHistory(sessionId))

            new SpiAgent.RequestModelEffect(
              spiModelProvider,
              systemMessage,
              req.userMessage,
              additionalContext,
              req.responseType,
              req.responseMapping,
              req.failureMapping,
              metadata,
              result => onSuccess(sessionMemoryClient, req.userMessage, result))

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

  private def deriveMemoryClient(memoryProvider: MemoryProvider): SessionMemory = {
    memoryProvider match {
      case p: MemoryProvider.Disabled =>
        new CoreMemoryClient(componentClient, MemorySettings.disabled())

      case p: MemoryProvider.LimitedWindowMemoryProvider =>
        new SessionMemoryClient(componentClient, new MemorySettings(p.read(), p.write(), p.readLastN()))

      case p: MemoryProvider.CustomMemoryProvider =>
        p.sessionMemory()

      case p: MemoryProvider.FromConfig => {
        val actualPath =
          if (p.configPath() == "")
            "akka.javasdk.agent.memory"
          else
            p.configPath()
        new SessionMemoryClient(componentClient, config.getConfig(actualPath))
      }
    }
  }

  private def onSuccess(
      sessionMemoryClient: SessionMemory,
      userMessage: String,
      modelResult: SpiAgent.ModelResult): Unit = {
    sessionMemoryClient.addInteraction(
      sessionId,
      componentId,
      new UserMessage(userMessage, modelResult.inputTokenCount),
      new AiMessage(modelResult.modelResponse, modelResult.outputTokenCount))
  }

  private def toSpiContextMessages(sessionHistory: SessionHistory): Vector[SpiAgent.ContextMessage] = {
    import scala.jdk.CollectionConverters._

    sessionHistory
      .messages()
      .asScala
      .flatMap {
        case m if m.isInstanceOf[AiMessage] =>
          Some(new SpiAgent.ContextMessage.AiMessage(m.asInstanceOf[AiMessage].text()))
        case m if m.isInstanceOf[UserMessage] =>
          Some(new SpiAgent.ContextMessage.UserMessage(m.asInstanceOf[UserMessage].text()))
        case m =>
          throw new IllegalStateException("Unsupported message type " + m.getClass.getName)
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
        new SpiAgent.ModelProvider.Custom(() => p.createChatModel(), () => p.createStreamingChatModel())
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

  override def serialize(message: Any): BytesPayload = {
    serializer.toBytes(message)
  }

  override def deserialize(modelResponse: String, responseType: Class[_]): Any = {
    try {
      if (responseType == classOf[String]) {
        modelResponse
      } else {
        // We might be able to bypass serialization roundtrip here, but might be good to catch invalid json
        // as early as possible.
        // The content type isn't used in this fromBytes.

        serializer.fromBytes(
          responseType,
          new BytesPayload(ByteString.fromString(modelResponse), JsonSerializer.JsonContentTypePrefix + "object"))
      }
    } catch {
      case e: IllegalArgumentException => throw new JsonParsingException(e.getMessage, e, modelResponse)
    }
  }

}
