/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import akka.annotation.InternalApi
import akka.http.scaladsl.model.HttpHeader
import akka.http.impl.util.JavaMapping.Implicits.AddAsScala
import akka.javasdk.DependencyProvider
import akka.javasdk.Metadata
import akka.javasdk.Tracing
import akka.javasdk.agent.Agent
import akka.javasdk.agent.AgentContext
import akka.javasdk.agent.JsonParsingException
import akka.javasdk.agent.MemoryProvider
import akka.javasdk.agent.ModelProvider
import akka.javasdk.agent.RemoteMcpTools
import akka.javasdk.agent.SessionHistory
import akka.javasdk.agent.SessionMemory
import akka.javasdk.agent.SessionMessage
import akka.javasdk.agent.SessionMessage.AiMessage
import akka.javasdk.agent.SessionMessage.ToolCallRequest
import akka.javasdk.agent.SessionMessage.ToolCallResponse
import akka.javasdk.agent.SessionMessage.UserMessage
import akka.javasdk.client.ComponentClient
import akka.javasdk.impl.AbstractContext
import akka.javasdk.impl.ComponentDescriptor
import akka.javasdk.impl.ErrorHandling.BadRequestException
import akka.javasdk.impl.HandlerNotFoundException
import akka.javasdk.impl.MetadataImpl
import akka.javasdk.impl.agent.BaseAgentEffectBuilder.ConstantSystemMessage
import akka.javasdk.impl.agent.BaseAgentEffectBuilder.NoPrimaryEffect
import akka.javasdk.impl.agent.BaseAgentEffectBuilder.RequestModel
import akka.javasdk.impl.agent.BaseAgentEffectBuilder.TemplateSystemMessage
import akka.javasdk.impl.agent.FunctionTools.FunctionToolInvoker
import akka.javasdk.impl.agent.SessionMemoryClient.MemorySettings
import akka.javasdk.impl.effect.ErrorReplyImpl
import akka.javasdk.impl.effect.MessageReplyImpl
import akka.javasdk.impl.effect.NoSecondaryEffectImpl
import akka.javasdk.impl.serialization.JsonSerializer
import akka.javasdk.impl.telemetry.SpanTracingImpl
import akka.javasdk.impl.telemetry.Telemetry
import akka.runtime.sdk.spi.BytesPayload
import akka.runtime.sdk.spi.RegionInfo
import akka.runtime.sdk.spi.SpiAgent
import akka.runtime.sdk.spi.SpiAgent.ContextMessage
import akka.runtime.sdk.spi.SpiMetadata
import akka.util.ByteString
import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import org.slf4j.LoggerFactory
import org.slf4j.MDC

import java.time.Instant
import scala.annotation.tailrec
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.jdk.CollectionConverters.SeqHasAsJava
import scala.util.control.NonFatal

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
      val span: Option[Span],
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
    sdkExecutionContext: ExecutionContext,
    tracerFactory: () => Tracer,
    serializer: JsonSerializer,
    componentDescriptor: ComponentDescriptor,
    regionInfo: RegionInfo,
    promptTemplateClient: PromptTemplateClient,
    componentClient: ComponentClient,
    overrideModelProvider: OverrideModelProvider,
    dependencyProvider: Option[DependencyProvider],
    config: Config)
    extends SpiAgent {
  import AgentImpl._

  private val router: ReflectiveAgentRouter[A] = {
    new ReflectiveAgentRouter[A](factory, componentDescriptor.methodInvokers, serializer)
  }

  override def handleCommand(command: SpiAgent.Command): Future[SpiAgent.Effect] = {

    val span: Option[Span] = command.span
    span.foreach(s => MDC.put(Telemetry.TRACE_ID, s.getSpanContext.getTraceId))
    // smuggling 0 arity methods called from the component client through here
    val cmdPayload = command.payload.getOrElse(BytesPayload.empty)
    val metadata: Metadata = MetadataImpl.of(command.metadata)
    val agentContext = new AgentContextImpl(sessionId, regionInfo.selfRegion, metadata, span, tracerFactory)

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
            val modelProvider = overrideModelProvider.getModelProviderForAgent(componentId).getOrElse(req.modelProvider)
            val spiModelProvider = toSpiModelProvider(modelProvider)
            val metadata = MetadataImpl.toSpi(req.replyMetadata)
            val sessionMemoryClient = deriveMemoryClient(req.memoryProvider)
            val additionalContext = toSpiContextMessages(sessionMemoryClient.getHistory(sessionId))
            val mcpToolEndpoints = toSpiMcpEndpoints(req.remoteMcpTools)

            // FIXME: we need FQCN to avoid clashes
            val toolDescriptors =
              ToolDescriptors.agentToolDescriptor(agent.getClass) ++
              req.toolInstancesOrClasses.flatMap {
                case cls: Class[_] => ToolDescriptors.forClass(cls)
                case any           => ToolDescriptors.forClass(any.getClass)
              }

            val functionTools = FunctionTools.agentFunctionToolInvokers(agent) ++
              req.toolInstancesOrClasses.flatMap {
                case cls: Class[_] => FunctionTools.forClass(cls, dependencyProvider)
                case any           => FunctionTools.forInstance(any)
              }.toMap

            new SpiAgent.RequestModelEffect(
              modelProvider = spiModelProvider,
              systemMessage = systemMessage,
              userMessage = req.userMessage,
              additionalContext = additionalContext,
              toolDescriptors = toolDescriptors,
              callToolFunction = request => callTool(functionTools, request)(sdkExecutionContext),
              mcpClientDescriptors = mcpToolEndpoints,
              responseType = req.responseType,
              responseMapping = req.responseMapping,
              failureMapping = req.failureMapping,
              replyMetadata = metadata,
              onSuccess = results => onSuccess(sessionMemoryClient, req.userMessage, results))

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
      span.foreach { _ =>
        MDC.remove(Telemetry.TRACE_ID)
      }
    }

  }

  private def callTool(functionTools: Map[String, FunctionToolInvoker], request: SpiAgent.ToolCallCommand)(implicit
      ex: ExecutionContext) = {

    val toolInvoker =
      functionTools.getOrElse(request.name, throw new IllegalArgumentException(s"Unknown tool ${request.name}"))

    val mapper = serializer.objectMapper
    val jsonNode = mapper.readTree(request.arguments)

    val methodInput =
      toolInvoker.paramNames.zipWithIndex.map { case (name, index) =>
        // assume that the paramName in the method matches a node from the json 'content'
        val node = jsonNode.get(name)
        val typ = toolInvoker.types(index)
        val javaType = mapper.getTypeFactory.constructType(typ)
        mapper.treeToValue(node, javaType).asInstanceOf[Any]
      }

    Future {
      val toolResult = toolInvoker.invoke(methodInput)

      if (toolInvoker.returnType == Void.TYPE)
        "SUCCESS"
      else if (toolInvoker.returnType == classOf[String])
        toolResult.asInstanceOf[String]
      else
        mapper.writeValueAsString(toolResult)
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
                  val newRequestPayload =
                    javaInterceptor.interceptRequest(toolCallRequest.name, toolCallRequest.arguments)
                  val newRequest =
                    if (newRequestPayload eq toolCallRequest.arguments) toolCallRequest
                    else new SpiAgent.ToolCallRequest(toolCallRequest.id, toolCallRequest.name, newRequestPayload)
                  toolCall(newRequest).map(result => javaInterceptor.interceptResponse(toolCallRequest.name, result))(
                    sdkExecutionContext)
                }

          })
      case other => throw new IllegalArgumentException(s"Unsupported remote mcp tools impl $other")
    }

  private def deriveMemoryClient(memoryProvider: MemoryProvider): SessionMemory = {
    memoryProvider match {
      case _: MemoryProvider.Disabled =>
        new SessionMemoryClient(componentClient, MemorySettings.disabled())

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
      responses: Seq[SpiAgent.Response]): Unit = {

    val timestamp = Instant.now()

    // AiMessages and ToolCallResponses
    val responseMessages: Seq[SessionMessage] =
      responses.map {
        case res: SpiAgent.ModelResponse =>
          val requests = res.toolRequests.map { req =>
            new ToolCallRequest(req.id, req.name, req.arguments)
          }.asJava
          new AiMessage(timestamp, res.content, componentId, requests)

        case res: SpiAgent.ToolCallResponse =>
          new ToolCallResponse(timestamp, componentId, res.id, res.name, res.content)
      }

    sessionMemoryClient.addInteraction(
      sessionId,
      new UserMessage(timestamp, userMessage, componentId),
      responseMessages.asJava)
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
        case m: ToolCallResponse =>
          new ContextMessage.ToolCallResponseMessage(m.id(), m.name(), m.text())
        case m =>
          throw new IllegalStateException("Unsupported message type " + m.getClass.getName)
      }
      .toVector
  }

  @tailrec
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
      case p: ModelProvider.GoogleAIGemini =>
        new SpiAgent.ModelProvider.GoogleAIGemini(
          p.apiKey(),
          p.modelName(),
          p.temperature(),
          p.topP(),
          p.maxOutputTokens())
      case p: ModelProvider.LocalAI =>
        new SpiAgent.ModelProvider.LocalAI(p.baseUrl(), p.modelName(), p.temperature(), p.topP(), p.maxTokens())
      case p: ModelProvider.Ollama =>
        new SpiAgent.ModelProvider.Ollama(p.baseUrl(), p.modelName(), p.temperature(), p.topP())
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
        case "anthropic"       => ModelProvider.Anthropic.fromConfig(providerConfig)
        case "googleai-gemini" => ModelProvider.GoogleAIGemini.fromConfig(providerConfig)
        case "ollama"          => ModelProvider.Ollama.fromConfig(providerConfig)
        case "openai"          => ModelProvider.OpenAi.fromConfig(providerConfig)
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
