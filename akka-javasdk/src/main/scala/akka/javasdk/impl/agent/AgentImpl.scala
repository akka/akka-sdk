/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import java.net.URI
import java.time.Instant
import java.util.concurrent.TimeUnit

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters._
import scala.jdk.DurationConverters.JavaDurationOps
import scala.jdk.OptionConverters.RichOptional
import scala.util.control.NonFatal

import akka.annotation.InternalApi
import akka.http.scaladsl.model.HttpHeader
import akka.javasdk.CommandException
import akka.javasdk.DependencyProvider
import akka.javasdk.Metadata
import akka.javasdk.Tracing
import akka.javasdk.agent
import akka.javasdk.agent.MessageContent.ImageMessageContent
import akka.javasdk.agent.MessageContent.ImageUrlMessageContent
import akka.javasdk.agent.SessionMessage.AiMessage
import akka.javasdk.agent.SessionMessage.MultimodalUserMessage
import akka.javasdk.agent.SessionMessage.ToolCallRequest
import akka.javasdk.agent.SessionMessage.ToolCallResponse
import akka.javasdk.agent.SessionMessage.UserMessage
import akka.javasdk.agent._
import akka.javasdk.client.ComponentClient
import akka.javasdk.impl.AbstractContext
import akka.javasdk.impl.ComponentDescriptor
import akka.javasdk.impl.ErrorHandling.BadRequestException
import akka.javasdk.impl.HandlerNotFoundException
import akka.javasdk.impl.JsonSchema
import akka.javasdk.impl.MetadataImpl
import akka.javasdk.impl.agent.BaseAgentEffectBuilder.ConstantSystemMessage
import akka.javasdk.impl.agent.BaseAgentEffectBuilder.NoPrimaryEffect
import akka.javasdk.impl.agent.BaseAgentEffectBuilder.RequestModel
import akka.javasdk.impl.agent.BaseAgentEffectBuilder.TemplateSystemMessage
import akka.javasdk.impl.agent.GuardrailProvider.AgentGuardrails
import akka.javasdk.impl.agent.SessionMemoryClient.MemorySettings
import akka.javasdk.impl.effect.ErrorReplyImpl
import akka.javasdk.impl.effect.MessageReplyImpl
import akka.javasdk.impl.effect.NoSecondaryEffectImpl
import akka.javasdk.impl.reflection.Reflect
import akka.javasdk.impl.serialization.JsonSerializer
import akka.javasdk.impl.telemetry.SpanTracingImpl
import akka.javasdk.impl.telemetry.Telemetry
import akka.runtime.sdk.spi.BytesPayload
import akka.runtime.sdk.spi.RegionInfo
import akka.runtime.sdk.spi.SpiAgent
import akka.runtime.sdk.spi.SpiAgent.ContextMessage
import akka.runtime.sdk.spi.SpiAgent.GuardrailFailure
import akka.runtime.sdk.spi.SpiAgent.InternalFailure
import akka.runtime.sdk.spi.SpiAgent.McpToolCallExecutionFailure
import akka.runtime.sdk.spi.SpiAgent.ModelFailure
import akka.runtime.sdk.spi.SpiAgent.OutputParsingFailure
import akka.runtime.sdk.spi.SpiAgent.RateLimitFailure
import akka.runtime.sdk.spi.SpiAgent.TimeoutFailure
import akka.runtime.sdk.spi.SpiAgent.ToolCallExecutionFailure
import akka.runtime.sdk.spi.SpiAgent.ToolCallLimitReachedFailure
import akka.runtime.sdk.spi.SpiAgent.UnsupportedFeatureFailure
import akka.runtime.sdk.spi.SpiAgent.{ AgentException => SpiAgentException }
import akka.runtime.sdk.spi.SpiMetadata
import akka.util.ByteString
import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.{ Context => OtelContext }
import org.slf4j.LoggerFactory
import org.slf4j.MDC

/**
 * INTERNAL API
 */
@InternalApi
private[impl] object AgentImpl {
  private val log = LoggerFactory.getLogger(classOf[AgentImpl[_]])

  private[impl] class AgentContextImpl(
      override val sessionId: String,
      override val selfRegion: String,
      override val metadata: Metadata,
      val telemetryContext: Option[OtelContext],
      tracerFactory: () => Tracer)
      extends AbstractContext
      with AgentContext {
    override def tracing(): Tracing = new SpanTracingImpl(telemetryContext, tracerFactory)
  }

  def modelProviderFromConfig(config: Config, configPath: String, componentId: String): ModelProvider = {
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
        case "anthropic"       => ModelProvider.Anthropic.fromConfig(providerConfig)
        case "googleai-gemini" => ModelProvider.GoogleAIGemini.fromConfig(providerConfig)
        case "hugging-face"    => ModelProvider.HuggingFace.fromConfig(providerConfig)
        case "ollama"          => ModelProvider.Ollama.fromConfig(providerConfig)
        case "openai"          => ModelProvider.OpenAi.fromConfig(providerConfig)
        case "local-ai"        => ModelProvider.LocalAI.fromConfig(providerConfig)
        case "bedrock"         => ModelProvider.Bedrock.fromConfig(providerConfig)
        case other =>
          throw new IllegalArgumentException(s"Unknown model provider [$other] in config [$resolvedConfigPath]")
      }
    } catch {
      case exc: ConfigException =>
        log.error("Invalid model provider configuration at [{}] for agent [{}].", resolvedConfigPath, componentId, exc)
        throw exc
    }
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
    sdkExecutionContext: ExecutionContext,
    tracerFactory: () => Tracer,
    serializer: JsonSerializer,
    componentDescriptor: ComponentDescriptor,
    regionInfo: RegionInfo,
    promptTemplateClient: Option[OtelContext] => PromptTemplateClient,
    componentClient: Option[OtelContext] => ComponentClient,
    overrideModelProvider: OverrideModelProvider,
    dependencyProvider: Option[DependencyProvider],
    guardrails: AgentGuardrails,
    config: Config)
    extends SpiAgent {
  import AgentImpl._

  private val router: ReflectiveAgentRouter[A] = {
    new ReflectiveAgentRouter[A](factory, componentDescriptor.methodInvokers, serializer)
  }

  override def handleCommand(command: SpiAgent.Command): Future[SpiAgent.Effect] =
    Future {

      val telemetryContext = Option(command.telemetryContext)
      val traceId = telemetryContext.flatMap { context =>
        Option(Span.fromContextOrNull(context)).map(_.getSpanContext.getTraceId)
      }
      traceId.foreach(id => MDC.put(Telemetry.TRACE_ID, id))

      // smuggling 0 arity methods called from the component client through here
      val cmdPayload = command.payload.getOrElse(BytesPayload.empty)
      val metadata: Metadata = MetadataImpl.of(command.metadata)
      val agentContext =
        new AgentContextImpl(sessionId, regionInfo.selfRegion, metadata, telemetryContext, tracerFactory)

      try {
        // we need the agent at this scope.
        // therefore, we initialize it exceptionally outside the router
        val agent = factory(agentContext)
        val commandEffect = router.handleCommand(agent, command.name, cmdPayload, agentContext)

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
            case ErrorReplyImpl(commandException) =>
              Left(new SpiAgent.Error(commandException.getMessage, Some(serializer.toBytes(commandException))))
            case MessageReplyImpl(message, m) =>
              val replyPayload = serializer.toBytes(message)
              val metadata = MetadataImpl.toSpi(m)
              Right(replyPayload -> metadata)
            case NoSecondaryEffectImpl =>
              throw new IllegalStateException("Expected reply or error")
          }
        }

        primaryEffect match {
          case req: RequestModel =>
            val systemMessage = req.systemMessage match {
              case ConstantSystemMessage(message) => message
              case template: TemplateSystemMessage =>
                promptTemplateClient(telemetryContext).getPromptTemplate(template.templateId).formatted(template.args)
            }
            val modelProvider = overrideModelProvider.getModelProviderForAgent(componentId).getOrElse(req.modelProvider)
            val spiModelProvider = toSpiModelProvider(modelProvider)
            val metadata = MetadataImpl.toSpi(req.replyMetadata)
            val sessionMemoryClient = deriveMemoryClient(req.memoryProvider, telemetryContext)
            val additionalContext = toSpiContextMessages(sessionMemoryClient.getHistory(sessionId))
            val mcpToolEndpoints = toSpiMcpEndpoints(req.mcpTools)

            val allToolClasses =
              agent.getClass +: req.toolInstancesOrClasses.map {
                case cls: Class[_] => cls
                case any           => any.getClass
              }

            // first, we need to validate them all against tool name clashes
            // unlikely, but better to crash it them have them shadowing each other
            FunctionTools.validateNames(allToolClasses)

            val toolDescriptors =
              allToolClasses.flatMap(FunctionTools.descriptorsFor)

            val functionTools =
              FunctionTools.toolInvokersFor(agent) ++
              req.toolInstancesOrClasses.flatMap {
                case cls: Class[_] if Reflect.isToolCandidate(cls) =>
                  FunctionTools.toolComponentInvokersFor(cls, componentClient(telemetryContext))
                case cls: Class[_] => FunctionTools.toolInvokersFor(cls, dependencyProvider)
                case any           => FunctionTools.toolInvokersFor(any)
              }.toMap

            val toolExecutor = new ToolExecutor(functionTools, serializer)

            val responseSchema =
              if (req.includeJsonSchema)
                Some(JsonSchema.jsonSchemaFor(req.responseType))
              else
                None

            val userMessageAt = Instant.now()

            val agentRole = Reflect.readAgentRole(agent.getClass)
            new SpiAgent.RequestModelEffect(
              modelProvider = spiModelProvider,
              systemMessage = systemMessage,
              userMessage = toSpiUserMessage(req.userMessage),
              additionalContext = additionalContext,
              toolDescriptors = toolDescriptors,
              callToolFunction = request => Future(toolExecutor.execute(request))(sdkExecutionContext),
              mcpClientDescriptors = mcpToolEndpoints,
              responseType = req.responseType,
              responseSchema = responseSchema,
              responseMapping = req.responseMapping,
              failureMapping = req.failureMapping.map(mapSpiAgentException),
              replyMetadata = metadata,
              onSuccess = results => onSuccess(sessionMemoryClient, req.userMessage, userMessageAt, agentRole, results),
              requestGuardrails = guardrails.modelRequestGuardrails,
              responseGuardrails = guardrails.modelResponseGuardrails)

          case NoPrimaryEffect =>
            errorOrReply match {
              case Left(err) =>
                new SpiAgent.ErrorEffect(err)
              case Right((reply, metadata)) =>
                new SpiAgent.ReplyEffect(reply, metadata)
            }
        }

      } catch {
        case e: CommandException =>
          val serializedException = serializer.toBytes(e)
          new SpiAgent.ErrorEffect(error = new SpiAgent.Error(e.getMessage, Some(serializedException)))
        case e: HandlerNotFoundException =>
          throw AgentException(command.name, e.getMessage, Some(e))
        case BadRequestException(msg) =>
          new SpiAgent.ErrorEffect(error = new SpiAgent.Error(msg, None))
        case e: AgentException => throw e
        case NonFatal(error) =>
          throw AgentException(command.name, s"Unexpected failure: $error", Some(error))
      } finally {
        if (traceId.isDefined) MDC.remove(Telemetry.TRACE_ID)
      }

    }(sdkExecutionContext)

  private def toSpiUserMessage(userMessage: agent.UserMessage): SpiAgent.UserMessage = {
    val contents = userMessage.contents().asScala.map(asd => toSpiMessageContent(asd))
    new SpiAgent.UserMessage(contents.toSeq)
  }

  private def toSpiMessageContent(messageContent: MessageContent): SpiAgent.MessageContent = {
    messageContent match {
      case content: ImageUrlMessageContent =>
        new SpiAgent.ImageUriMessageContent(
          content.url().toURI,
          toSpiDetailLevel(content.detailLevel()),
          content.mimeType().toScala)
      case content: MessageContent.TextMessageContent =>
        new SpiAgent.TextMessageContent(content.text())
    }
  }

  private def toSpiDetailLevel(level: ImageMessageContent.DetailLevel): SpiAgent.ImageMessageContent.DetailLevel = {
    level match {
      case ImageMessageContent.DetailLevel.LOW  => SpiAgent.ImageMessageContent.Low
      case ImageMessageContent.DetailLevel.HIGH => SpiAgent.ImageMessageContent.High
      case ImageMessageContent.DetailLevel.AUTO => SpiAgent.ImageMessageContent.Auto
    }
  }

  private def toSpiMcpEndpoints(remoteMcpTools: Seq[RemoteMcpTools]): Seq[SpiAgent.McpToolEndpointDescriptor] =
    remoteMcpTools.map {
      case remoteMcp: RemoteMcpToolsImpl =>
        new SpiAgent.McpToolEndpointDescriptor(
          mcpEndpoint = remoteMcp.serverUri,
          additionalClientHeaders = remoteMcp.additionalClientHeaders.map(
            _.asInstanceOf[HttpHeader] // javadsl headers are always scala headers?
          ),
          toolNameFilter = remoteMcp.toolNameFilter match {
            case Some(predicate) => predicate.test
            case None            => (_: String) => true
          },
          toolInterceptor = remoteMcp.interceptor.map {
            javaInterceptor =>
              (toolCallRequest: SpiAgent.ToolCallRequest, toolCall: SpiAgent.ToolCallRequest => Future[String]) =>
                {
                  val interceptorContext = new RemoteMcpTools.ToolInterceptorContext {
                    override def toolName(): String = toolCallRequest.name
                  }
                  val newRequestPayload =
                    javaInterceptor.interceptRequest(interceptorContext, toolCallRequest.arguments)
                  val newRequest =
                    if (newRequestPayload eq toolCallRequest.arguments) toolCallRequest
                    else new SpiAgent.ToolCallRequest(toolCallRequest.id, toolCallRequest.name, newRequestPayload)
                  toolCall(newRequest).map(result =>
                    javaInterceptor.interceptResponse(interceptorContext, newRequestPayload, result))(
                    sdkExecutionContext)
                }

          },
          toolTimeout =
            if (remoteMcp.timeout == Duration.Zero) None
            else Some(remoteMcp.timeout),
          requestGuardrails = guardrails.mcpToolRequestGuardrails,
          responseGuardrails = guardrails.mcpToolResponseGuardrails)
      case other => throw new IllegalArgumentException(s"Unsupported remote mcp tools impl $other")
    }

  private def deriveMemoryClient(
      memoryProvider: MemoryProvider,
      telemetryContext: Option[OtelContext]): SessionMemory = {
    memoryProvider match {
      case _: MemoryProvider.Disabled =>
        new SessionMemoryClient(componentClient(telemetryContext), MemorySettings.disabled())

      case p: MemoryProvider.LimitedWindowMemoryProvider =>
        new SessionMemoryClient(
          componentClient(telemetryContext),
          new MemorySettings(p.read(), p.write(), p.readLastN(), p.filters()))

      case p: MemoryProvider.CustomMemoryProvider =>
        p.sessionMemory()

      case p: MemoryProvider.FromConfig =>
        val actualPath =
          if (p.configPath() == "")
            "akka.javasdk.agent.memory"
          else
            p.configPath()
        new SessionMemoryClient(componentClient(telemetryContext), config.getConfig(actualPath))
    }
  }

  private def onSuccess(
      sessionMemoryClient: SessionMemory,
      userMessage: agent.UserMessage,
      userMessageAt: Instant,
      agentRole: Option[String],
      responses: Seq[SpiAgent.Response]): Unit = {

    // AiMessages and ToolCallResponses
    val responseMessages: Seq[SessionMessage] =
      responses.map {
        case res: SpiAgent.ModelResponse =>
          val requests = res.toolRequests.map { req =>
            new ToolCallRequest(req.id, req.name, req.arguments)
          }.asJava
          new AiMessage(res.timestamp, res.content, componentId, requests)

        case res: SpiAgent.ToolCallResponse =>
          new ToolCallResponse(res.timestamp, componentId, res.id, res.name, res.content)
      }

    if (userMessage.isTextOnly) {
      sessionMemoryClient.addInteraction(
        sessionId,
        new UserMessage(userMessageAt, userMessage.text(), componentId),
        responseMessages.asJava)
    } else {
      val contents = userMessage
        .contents()
        .asScala
        .map(s => toSessionMemoryContent(s))
        .asJava
      sessionMemoryClient.addInteraction(
        sessionId,
        new MultimodalUserMessage(userMessageAt, contents, componentId),
        responseMessages.asJava)
    }
  }

  private def toSessionMemoryContent(messageContent: MessageContent): SessionMessage.MessageContent = {
    messageContent match {
      case content: MessageContent.TextMessageContent =>
        new SessionMessage.MessageContent.TextMessageContent(content.text)
      case content: ImageUrlMessageContent =>
        new SessionMessage.MessageContent.ImageUriMessageContent(
          content.url().toString,
          content.detailLevel(),
          content.mimeType())
    }
  }

  private def toSpiContextMessages(sessionHistory: SessionHistory): Vector[SpiAgent.ContextMessage] = {
    import scala.jdk.CollectionConverters._

    sessionHistory
      .messages()
      .asScala
      .map {
        case m: AiMessage =>
          val toolRequests = m
            .toolCallRequests()
            .asScala
            .map { req =>
              new SpiAgent.ToolCallRequest(req.id(), req.name(), req.arguments())
            }
            .toSeq
          new SpiAgent.ContextMessage.AiMessage(m.text(), toolRequests)
        case m: UserMessage =>
          new SpiAgent.ContextMessage.UserMessage(m.text())

        case m: MultimodalUserMessage =>
          val contents = m
            .contents()
            .asScala
            .map {
              case content: SessionMessage.MessageContent.TextMessageContent =>
                new SpiAgent.TextMessageContent(content.text())
              case content: SessionMessage.MessageContent.ImageUriMessageContent =>
                new SpiAgent.ImageUriMessageContent(
                  URI.create(content.uri()),
                  toSpiDetailLevel(content.detailLevel()),
                  content.mimeType().toScala)
            }
            .toSeq
          new SpiAgent.ContextMessage.UserMessage(contents)
        case m: ToolCallResponse =>
          new ContextMessage.ToolCallResponseMessage(m.id(), m.name(), m.text())
        case m =>
          throw new IllegalStateException("Unsupported message type " + m.getClass.getName)
      }
      .toVector
  }

  private def mapSpiAgentException(func: Throwable => Any): Throwable => Any = {

    def convert(thw: Throwable): Throwable = thw match {
      case exc: SpiAgentException =>
        try {
          exc.reason match {
            case ModelFailure              => new ModelException(exc.getMessage)
            case RateLimitFailure          => new RateLimitException(exc.getMessage)
            case TimeoutFailure            => new ModelTimeoutException(exc.getMessage)
            case UnsupportedFeatureFailure => new UnsupportedFeatureException(exc.getMessage)
            case InternalFailure           => new InternalServerException(exc.getMessage)

            case ToolCallLimitReachedFailure => new ToolCallLimitReachedException(exc.getMessage)
            case reason: ToolCallExecutionFailure =>
              new ToolCallExecutionException(exc.getMessage, reason.toolName, exc.cause)
            case reason: McpToolCallExecutionFailure =>
              new McpToolCallExecutionException(exc.getMessage, reason.toolName, reason.endpoint, exc.cause)

            case reason: GuardrailFailure =>
              new Guardrail.GuardrailException(reason.explanation)

            // this is expected to be a JsonParsingException, we give it as is to users
            case OutputParsingFailure => exc.cause
          }
        } catch {
          case _: MatchError =>
            // to cover SPI evolution, new reasons may not exist in the SDK
            new RuntimeException(exc.getMessage, exc.cause)
        }
      case other => other // unknown and thus unmapped
    }

    (throwable: Throwable) => func(convert(throwable))
  }

  @tailrec
  private def toSpiModelProvider(modelProvider: ModelProvider): SpiAgent.ModelProvider = {
    modelProvider match {
      case p: ModelProvider.FromConfig =>
        toSpiModelProvider(modelProviderFromConfig(config, p.configPath(), componentId))
      case p: ModelProvider.Anthropic =>
        new SpiAgent.ModelProvider.Anthropic(
          apiKey = p.apiKey,
          modelName = p.modelName,
          baseUrl = p.baseUrl,
          temperature = p.temperature,
          topP = p.topP,
          topK = p.topK,
          maxTokens = p.maxTokens,
          new SpiAgent.ModelSettings(p.connectionTimeout().toScala, p.responseTimeout().toScala, p.maxRetries()))
      case p: ModelProvider.GoogleAIGemini =>
        new SpiAgent.ModelProvider.GoogleAIGemini(
          p.apiKey(),
          p.modelName(),
          p.baseUrl(),
          p.temperature(),
          p.topP(),
          p.maxOutputTokens(),
          new SpiAgent.ModelSettings(p.connectionTimeout().toScala, p.responseTimeout().toScala, p.maxRetries()))
      case p: ModelProvider.HuggingFace =>
        new SpiAgent.ModelProvider.HuggingFace(
          p.accessToken(),
          p.modelId(),
          p.baseUrl(),
          p.temperature(),
          p.topP(),
          p.maxNewTokens(),
          new SpiAgent.ModelSettings(p.connectionTimeout().toScala, p.responseTimeout().toScala, p.maxRetries()))
      case p: ModelProvider.LocalAI =>
        new SpiAgent.ModelProvider.LocalAI(p.baseUrl(), p.modelName(), p.temperature(), p.topP(), p.maxTokens())
      case p: ModelProvider.Ollama =>
        new SpiAgent.ModelProvider.Ollama(
          p.baseUrl(),
          p.modelName(),
          p.temperature(),
          p.topP(),
          new SpiAgent.ModelSettings(p.connectionTimeout().toScala, p.responseTimeout().toScala, p.maxRetries()))
      case p: ModelProvider.OpenAi =>
        new SpiAgent.ModelProvider.OpenAi(
          apiKey = p.apiKey,
          modelName = p.modelName,
          baseUrl = p.baseUrl,
          temperature = p.temperature,
          topP = p.topP,
          maxTokens = p.maxTokens,
          maxCompletionTokens = p.maxCompletionTokens,
          new SpiAgent.ModelSettings(p.connectionTimeout().toScala, p.responseTimeout().toScala, p.maxRetries()))
      case p: ModelProvider.Custom =>
        new SpiAgent.ModelProvider.Custom(() => p.createChatModel(), () => p.createStreamingChatModel())
      case p: ModelProvider.Bedrock =>
        new SpiAgent.ModelProvider.Bedrock(
          region = p.region,
          modelId = p.modelId,
          returnThinking = p.returnThinking,
          sendThinking = p.sendThinking,
          maxOutputTokens = p.maxOutputTokens,
          reasoningTokenBudget = p.reasoningTokenBudget,
          additionalModelRequestFields = p.additionalModelRequestFields.asScala.toMap,
          accessToken = p.accessToken,
          temperature = p.temperature,
          topP = p.topP,
          maxTokens = p.maxTokens,
          modelSettings = new SpiAgent.ModelSettings(
            FiniteDuration.apply(30, TimeUnit.SECONDS),
            p.responseTimeout().toScala,
            p.maxRetries()))
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
