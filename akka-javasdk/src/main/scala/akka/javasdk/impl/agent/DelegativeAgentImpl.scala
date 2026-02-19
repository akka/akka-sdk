/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._

import akka.annotation.InternalApi
import akka.javasdk.DependencyProvider
import akka.javasdk.Metadata
import akka.javasdk.agent.Agent
import akka.javasdk.agent.AgentContext
import akka.javasdk.agent.Delegative
import akka.javasdk.agent.ModelProvider
import akka.javasdk.client.ComponentClient
import akka.javasdk.impl.JsonSchema
import akka.javasdk.impl.agent.AgentImpl.AgentContextImpl
import akka.javasdk.impl.reflection.Reflect
import akka.javasdk.impl.serialization.JsonSerializer
import akka.runtime.sdk.spi.BytesPayload
import akka.runtime.sdk.spi.RegionInfo
import akka.runtime.sdk.spi.SpiAgent
import akka.runtime.sdk.spi.SpiDelegativeAgent
import akka.runtime.sdk.spi.SpiJsonSchema
import akka.util.ByteString
import com.typesafe.config.Config
import io.opentelemetry.api.trace.Tracer

/**
 * INTERNAL API
 */
@InternalApi
private[impl] class DelegativeAgentImpl[A <: Agent](
    componentId: String,
    sessionId: String,
    factory: AgentContext => A,
    sdkExecutionContext: ExecutionContext,
    tracerFactory: () => Tracer,
    serializer: JsonSerializer,
    regionInfo: RegionInfo,
    overrideModelProvider: OverrideModelProvider,
    dependencyProvider: Option[DependencyProvider],
    componentClient: ComponentClient,
    agentRegistry: () => AgentRegistryImpl,
    config: Config)
    extends SpiDelegativeAgent {

  private val agentInstance = {
    val agentContext =
      new AgentContextImpl(sessionId, regionInfo.selfRegion, Metadata.EMPTY, telemetryContext = None, tracerFactory)
    factory(agentContext)
  }

  private def delegative = agentInstance.asInstanceOf[Delegative[Any, Any]]

  private def agentClass: Class[_] = delegative.getClass

  override def setInput(input: BytesPayload): Unit = {
    if (!input.bytes.isEmpty) {
      val inputType = Reflect.getDelegativeInputType(agentClass)
      val deserialized = serializer.fromBytes(inputType, input)
      agentInstance._internalSetInput(deserialized)
    }
  }

  override def instructions(): String = delegative.instructions()

  override def delegations(): Seq[SpiDelegativeAgent.SpiDelegation] = {
    val registry = agentRegistry()
    delegative
      .delegations()
      .asScala
      .map { delegation =>
        val agentId = delegation.agentComponentId()
        val description = delegation.description().toScala
        val delegateeClass = registry.agentClassById.getOrElse(
          agentId,
          throw new IllegalArgumentException(s"Unknown agent [$agentId] for delegation"))
        delegation match {
          case _: DelegativeAgentDelegation =>
            // Always wrap as {"input": type} so the schema structure is consistent with AgentDelegation
            val inputType = Reflect.getDelegativeInputType(delegateeClass)
            val inputSchema = new SpiJsonSchema.JsonSchemaObject(
              None,
              Map("input" -> JsonSchema.jsonSchemaFor(inputType)),
              Seq("input"))
            new SpiDelegativeAgent.DelegativeAgentDelegation(agentId, description, inputSchema)

          case _: AgentDelegation =>
            val handler = Reflect
              .getCommandHandlerMethod(delegateeClass)
              .getOrElse(throw new IllegalStateException(s"Agent [$agentId] should have one command handler"))
            val commandName = handler.getName.capitalize
            val inputSchema = JsonSchema.jsonSchemaFor(handler)
            new SpiDelegativeAgent.AgentDelegation(agentId, description, commandName, inputSchema)
        }
      }
      .toSeq
  }

  override def toolDescriptors(): Seq[SpiAgent.ToolDescriptor] = {
    val toolClasses = delegative
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
    val invokers = delegative
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

  override def resultType(): Class[_] = delegative.resultType()

  override def resultSchema(): Option[SpiJsonSchema.JsonSchemaDataType] =
    Some(JsonSchema.jsonSchemaFor(delegative.resultType()))

  override def modelProvider(): SpiAgent.ModelProvider = {
    val provider = overrideModelProvider.getModelProviderForAgent(componentId).getOrElse(ModelProvider.fromConfig(""))
    AgentImpl.toSpiModelProvider(provider, config, componentId)
  }

  override def maxTurns(): Int = delegative.maxTurns()

  override def handleCommand(command: SpiAgent.Command): Future[SpiAgent.Effect] =
    throw new UnsupportedOperationException("handleCommand is not used for delegative agents")

  override def serialize(message: Any): BytesPayload = serializer.toBytes(message)

  override def deserialize(modelResponse: String, responseType: Class[_]): Any = {
    if (responseType == classOf[String]) modelResponse
    else
      serializer.fromBytes(
        responseType,
        new BytesPayload(ByteString.fromString(modelResponse), JsonSerializer.JsonContentTypePrefix + "object"))
  }

  override def serializeGetResult(
      result: Option[BytesPayload],
      failed: Option[String],
      currentTurn: Int): BytesPayload = {
    val resultValue: Delegative.Result[_] = result match {
      case Some(resultBytes) =>
        val value = serializer.fromBytes(delegative.resultType(), resultBytes)
        new Delegative.Result.Completed(value)
      case None if failed.isDefined =>
        new Delegative.Result.Failed(failed.get)
      case None =>
        new Delegative.Result.Running(currentTurn)
    }
    serializer.toBytes(resultValue)
  }

  override def serializeDelegationInput(agentComponentId: String, toolCallArguments: String): BytesPayload = {
    // toolCallArguments is always {"paramName": value} — either {"paramName": value} for AgentDelegation
    // or {"input": value} for DelegativeAgentDelegation. Extract the single property value.
    val valueJson = extractSinglePropertyValue(toolCallArguments)
    new BytesPayload(ByteString.fromString(valueJson), JsonSerializer.JsonContentTypePrefix + "object")
  }

  private def extractSinglePropertyValue(jsonArgs: String): String = {
    val argsNode = serializer.objectMapper.readTree(jsonArgs)
    val fieldName = argsNode.fieldNames().next()
    serializer.objectMapper.writeValueAsString(argsNode.get(fieldName))
  }
}
