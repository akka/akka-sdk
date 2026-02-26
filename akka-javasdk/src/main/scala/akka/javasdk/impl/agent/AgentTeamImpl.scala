/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import java.time.Instant
import java.util.Optional

import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._

import akka.Done
import akka.actor.typed.ActorSystem
import akka.annotation.InternalApi
import akka.javasdk.DependencyProvider
import akka.javasdk.Metadata
import akka.javasdk.agent.Agent
import akka.javasdk.agent.AgentContext
import akka.javasdk.agent.AgentTeam
import akka.javasdk.agent.AgentTeam.Delegation
import akka.javasdk.agent.MemoryProvider
import akka.javasdk.agent.ModelProvider
import akka.javasdk.agent.SessionMemory
import akka.javasdk.agent.SessionMessage
import akka.javasdk.agent.SessionMessage.AiMessage
import akka.javasdk.agent.SessionMessage.MultimodalUserMessage
import akka.javasdk.agent.SessionMessage.ToolCallRequest
import akka.javasdk.agent.SessionMessage.ToolCallResponse
import akka.javasdk.agent.SessionMessage.UserMessage
import akka.javasdk.client.ComponentClient
import akka.javasdk.impl.JsonSchema
import akka.javasdk.impl.agent.AgentImpl.AgentContextImpl
import akka.javasdk.impl.agent.SessionMemoryClient.MemorySettings
import akka.javasdk.impl.reflection.Reflect
import akka.javasdk.impl.serialization.JsonSerializer
import akka.javasdk.impl.serialization.Serializer
import akka.runtime.sdk.spi.BytesPayload
import akka.runtime.sdk.spi.RegionInfo
import akka.runtime.sdk.spi.SpiAgent
import akka.runtime.sdk.spi.SpiAgentTeam
import akka.runtime.sdk.spi.SpiJsonSchema
import akka.util.ByteString
import com.typesafe.config.Config
import io.opentelemetry.api.trace.Tracer

/**
 * INTERNAL API
 */
@InternalApi
private[impl] class AgentTeamImpl[A <: Agent](
    componentId: String,
    sessionId: String,
    factory: AgentContext => A,
    sdkExecutionContext: ExecutionContext,
    tracerFactory: () => Tracer,
    serializer: Serializer,
    regionInfo: RegionInfo,
    overrideModelProvider: OverrideModelProvider,
    dependencyProvider: Option[DependencyProvider],
    componentClient: ComponentClient,
    agentRegistry: () => AgentRegistryImpl,
    config: Config,
    _system: ActorSystem[_])
    extends SpiAgentTeam {

  implicit val system: ActorSystem[_] = _system

  private val agentInstance = {
    val agentContext =
      new AgentContextImpl(sessionId, regionInfo.selfRegion, Metadata.EMPTY, telemetryContext = None, tracerFactory)
    factory(agentContext)
  }

  private def agentTeam = agentInstance.asInstanceOf[AgentTeam[Any, Any]]

  private def agentClass: Class[_] = agentTeam.getClass

  private lazy val sessionMemoryClient: SessionMemory =
    deriveMemoryClient(agentTeam.memoryProvider())

  private def deriveMemoryClient(memoryProvider: MemoryProvider): SessionMemory =
    memoryProvider match {
      case _: MemoryProvider.Disabled =>
        new SessionMemoryClient(componentClient, MemorySettings.disabled())

      case p: MemoryProvider.LimitedWindowMemoryProvider =>
        new SessionMemoryClient(componentClient, new MemorySettings(p.read(), p.write(), p.readLastN(), p.filters()))

      case p: MemoryProvider.CustomMemoryProvider =>
        p.sessionMemory()

      case p: MemoryProvider.FromConfig =>
        val actualPath =
          if (p.configPath() == "")
            "akka.javasdk.agent.memory"
          else
            p.configPath()
        new SessionMemoryClient(componentClient, config.getConfig(actualPath))
    }

  override def setInput(input: BytesPayload): Unit = {
    if (!input.bytes.isEmpty) {
      val inputType = Reflect.getAgentTeamInputType(agentClass)
      val deserialized = serializer.fromBytes(inputType, input)
      agentInstance._internalSetInput(deserialized)
    }
  }

  override def instructions(): String = agentTeam.instructions()

  override def delegations(): Seq[SpiAgentTeam.SpiDelegation] = {
    val registry = agentRegistry()
    agentTeam
      .delegations()
      .asScala
      .map { delegation =>
        val agentId = delegation.agentComponentId()
        val description = delegation.description().toScala
        val delegateeClass = registry.agentClassById.getOrElse(
          agentId,
          throw new IllegalArgumentException(s"Unknown agent [$agentId] for delegation"))
        delegation match {
          case _: AgentTeamDelegation =>
            // Always wrap as {"input": type} so the schema structure is consistent with AgentDelegation
            val inputType = Reflect.getAgentTeamInputType(delegateeClass)
            val inputSchema = new SpiJsonSchema.JsonSchemaObject(
              None,
              Map("input" -> JsonSchema.jsonSchemaFor(inputType)),
              Seq("input"))
            new SpiAgentTeam.AgentTeamDelegation(agentId, description, inputSchema)

          case _: AgentDelegation =>
            val handler = Reflect
              .getCommandHandlerMethod(delegateeClass)
              .getOrElse(throw new IllegalStateException(s"Agent [$agentId] should have one command handler"))
            val commandName = handler.getName.capitalize
            val inputSchema = JsonSchema.jsonSchemaFor(handler)
            new SpiAgentTeam.AgentDelegation(agentId, description, commandName, inputSchema)
        }
      }
      .toSeq
  }

  override def toolDescriptors(): Seq[SpiAgent.ToolDescriptor] = {
    val toolClasses = agentTeam
      .tools()
      .asScala
      .map {
        case cls: Class[_] => cls
        case any           => any.getClass
      }
      .toSeq
    toolClasses.flatMap(FunctionTools.descriptorsFor)
  }

  override def callToolFunction(): SpiAgent.ToolCallCommand => Future[String] = {
    val invokers = agentTeam
      .tools()
      .asScala
      .flatMap {
        case cls: Class[_] if Reflect.isToolCandidate(cls) =>
          FunctionTools.toolComponentInvokersFor(cls, componentClient)
        case cls: Class[_] =>
          FunctionTools.toolInvokersFor(cls, dependencyProvider)
        case any =>
          FunctionTools.toolInvokersFor(any)
      }
      .toMap
    val executor = new ToolExecutor(invokers, serializer)
    request => Future(executor.execute(request))(sdkExecutionContext)
  }

  override def mcpClientDescriptors(): Seq[SpiAgent.McpToolEndpointDescriptor] = Nil

  override def requestGuardrails(): Seq[SpiAgent.Guardrail] = Nil

  override def responseGuardrails(): Seq[SpiAgent.Guardrail] = Nil

  override def getSessionHistory(sessionId: String): Future[Seq[SpiAgent.ContextMessage]] =
    Future {
      val history = sessionMemoryClient.getHistory(sessionId)
      AgentImpl.toSpiContextMessages(history)
    }(sdkExecutionContext)

  override def addToSessionHistory(sessionId: String, messages: Seq[SpiAgent.ContextMessage]): Future[Done] =
    Future {
      groupByUserMessage(messages).foreach { case (userMsgOpt, rest) =>
        val now = Instant.now()
        val sessionResponses = toSessionMessages(now, rest)
        userMsgOpt match {
          case Some(userMsg) if userMsg.contents.forall(_.isInstanceOf[SpiAgent.TextMessageContent]) =>
            val text = userMsg.contents.collect { case t: SpiAgent.TextMessageContent => t.text }.mkString(" ")
            sessionMemoryClient.addInteraction(
              sessionId,
              new UserMessage(now, text, componentId),
              sessionResponses.asJava)
          case Some(userMsg) =>
            val contents = userMsg.contents.map {
              case t: SpiAgent.TextMessageContent =>
                new SessionMessage.MessageContent.TextMessageContent(t.text): SessionMessage.MessageContent
              case img: SpiAgent.ImageUriMessageContent =>
                new SessionMessage.MessageContent.ImageUriMessageContent(
                  img.uri.toString,
                  AgentImpl.fromSpiDetailLevel(img.detailLevel),
                  img.mimeType.toJava): SessionMessage.MessageContent
            }.asJava
            sessionMemoryClient.addInteraction(
              sessionId,
              new MultimodalUserMessage(now, contents, componentId),
              sessionResponses.asJava)
          case None =>
            sessionMemoryClient.addInteraction(sessionId, null.asInstanceOf[UserMessage], sessionResponses.asJava)
        }
      }
      Done
    }(sdkExecutionContext)

  private def groupByUserMessage(messages: Seq[SpiAgent.ContextMessage])
      : Seq[(Option[SpiAgent.ContextMessage.UserMessage], Seq[SpiAgent.ContextMessage])] = {
    val result = ListBuffer.empty[(Option[SpiAgent.ContextMessage.UserMessage], Seq[SpiAgent.ContextMessage])]
    var currentUser: Option[SpiAgent.ContextMessage.UserMessage] = None
    var currentRest = ListBuffer.empty[SpiAgent.ContextMessage]

    messages.foreach {
      case u: SpiAgent.ContextMessage.UserMessage =>
        if (currentUser.isDefined || currentRest.nonEmpty) {
          result += ((currentUser, currentRest.toSeq))
        }
        currentUser = Some(u)
        currentRest = ListBuffer.empty
      case other =>
        currentRest += other
    }
    if (currentUser.isDefined || currentRest.nonEmpty) {
      result += ((currentUser, currentRest.toSeq))
    }
    result.toSeq
  }

  private def toSessionMessages(now: Instant, messages: Seq[SpiAgent.ContextMessage]): Seq[SessionMessage] =
    messages.collect {
      case m: SpiAgent.ContextMessage.AiMessage =>
        val toolCallRequests = m.toolRequests.map { req =>
          new ToolCallRequest(req.id, req.name, req.arguments)
        }.asJava
        new AiMessage(now, m.content, componentId, toolCallRequests, m.thinking.toJava, m.attributes.asJava)
      case m: SpiAgent.ContextMessage.ToolCallResponseMessage =>
        new ToolCallResponse(now, componentId, m.id, m.name, m.content)
    }

  override def resultType(): Class[_] = agentTeam.resultType()

  override def resultSchema(): Option[SpiJsonSchema.JsonSchemaDataType] =
    Some(JsonSchema.jsonSchemaFor(agentTeam.resultType()))

  override def modelProvider(): SpiAgent.ModelProvider = {
    val provider = overrideModelProvider.getModelProviderForAgent(componentId).getOrElse(ModelProvider.fromConfig(""))
    AgentImpl.toSpiModelProvider(provider, config, componentId)
  }

  override def maxTurns(): Int = agentTeam.maxTurns()

  // FIXME probably missing something for withDetailedReply

  override def handleCommand(command: SpiAgent.Command): Future[SpiAgent.Effect] =
    throw new UnsupportedOperationException("handleCommand is not used for agent team")

  override def serialize(message: Any): BytesPayload = serializer.toBytes(message)

  override def deserialize(modelResponse: String, responseType: Class[_]): Any = {
    if (responseType == classOf[String]) modelResponse
    else
      serializer.fromBytes(
        responseType,
        new BytesPayload(ByteString.fromString(modelResponse), JsonSerializer.JsonContentTypePrefix + "object"))
  }

  override def serializeGetResult(result: SpiAgentTeam.Result): BytesPayload = {
    val resultValue: AgentTeam.Result[_] = result.resultPayload match {
      case Some(resultBytes) =>
        val value = serializer.fromBytes(agentTeam.resultType(), resultBytes)
        new AgentTeam.Result.Completed(value)
      case None if result.failed.isDefined =>
        new AgentTeam.Result.Failed(result.failed.get)
      case None =>
        new AgentTeam.Result.Running(result.currentTurn)
    }
    serializer.toBytes(resultValue)
  }

  override def serializeDelegationInput(agentComponentId: String, toolCallArguments: String): BytesPayload = {
    // toolCallArguments is always {"paramName": value} — either {"paramName": value} for AgentDelegation
    // or {"input": value} for AgentTeamDelegation. Extract the single property value.
    val valueJson = extractSinglePropertyValue(toolCallArguments)
    new BytesPayload(ByteString.fromString(valueJson), JsonSerializer.JsonContentTypePrefix + "object")
  }

  private def extractSinglePropertyValue(jsonArgs: String): String = {
    val argsNode = serializer.objectMapper.readTree(jsonArgs)
    val fieldName = argsNode.fieldNames().next()
    serializer.objectMapper.writeValueAsString(argsNode.get(fieldName))
  }
}

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] object DelegationImpl {
  def toAgent[A <: Agent](agentClass: Class[A]): Delegation = {
    val componentId = Reflect.readComponentId(agentClass)
    val description = Reflect.readAgentDescription(agentClass).toJava
    if (Reflect.isAgentTeam(agentClass))
      AgentTeamDelegation(componentId, description)
    else
      AgentDelegation(componentId, description)
  }
}

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] final case class AgentDelegation(agentComponentId: String, description: Optional[String])
    extends Delegation {
  override def withDescription(description: String): Delegation =
    copy(description = Optional.ofNullable(description))
}

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] final case class AgentTeamDelegation(agentComponentId: String, description: Optional[String])
    extends Delegation {
  override def withDescription(description: String): Delegation =
    copy(description = Optional.ofNullable(description))
}
