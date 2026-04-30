/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent.autonomous

import java.util

import akka.annotation.InternalApi
import akka.javasdk.agent.Guardrail
import akka.javasdk.agent.ModelProvider
import akka.javasdk.agent.RemoteMcpTools
import akka.javasdk.agent.autonomous.AgentDefinition
import akka.javasdk.agent.autonomous.capability.AgentCapability

/**
 * INTERNAL API
 */
@InternalApi
final case class AgentDefinitionImpl(
    purpose: String,
    guidance: Option[String],
    modelProvider: ModelProvider,
    toolInstancesOrClasses: util.List[AnyRef],
    mcpTools: util.List[RemoteMcpTools],
    requestGuardrailClassNames: util.List[String],
    responseGuardrailClassNames: util.List[String],
    capabilities: util.List[AgentCapability])
    extends AgentDefinition {

  override def purpose(purpose: String): AgentDefinition =
    copy(purpose = purpose)

  override def guidance(guidance: String): AgentDefinition =
    copy(guidance = Some(guidance))

  override def capability(capability: AgentCapability): AgentDefinition =
    copy(capabilities = concat(this.capabilities, Seq(capability)))

  override def modelProvider(provider: ModelProvider): AgentDefinition =
    copy(modelProvider = provider)

  override def tools(tools: AnyRef*): AgentDefinition =
    copy(toolInstancesOrClasses = concat(toolInstancesOrClasses, tools))

  override def mcpTools(tools: RemoteMcpTools*): AgentDefinition =
    copy(mcpTools = concat(mcpTools, tools))

  override def requestGuardrails(guardrails: Class[_ <: Guardrail]*): AgentDefinition =
    copy(requestGuardrailClassNames = concat(requestGuardrailClassNames, guardrails.map(_.getName)))

  override def responseGuardrails(guardrails: Class[_ <: Guardrail]*): AgentDefinition =
    copy(responseGuardrailClassNames = concat(responseGuardrailClassNames, guardrails.map(_.getName)))

  private def concat[T](existing: util.List[T], additions: Seq[T]): util.List[T] = {
    val result = new util.ArrayList[T](existing)
    additions.foreach(result.add)
    util.Collections.unmodifiableList(result)
  }
}

/**
 * INTERNAL API
 */
@InternalApi
object AgentDefinitionImpl {
  def empty(): AgentDefinitionImpl =
    AgentDefinitionImpl(
      purpose = "",
      guidance = None,
      modelProvider = null,
      toolInstancesOrClasses = util.List.of(),
      mcpTools = util.List.of(),
      requestGuardrailClassNames = util.List.of(),
      responseGuardrailClassNames = util.List.of(),
      capabilities = util.List.of())

  /**
   * Combine purpose and optional guidance into a single system prompt string. Used internally until the runtime SPI has
   * separate fields for purpose and guidance.
   */
  def composeSystemPrompt(purpose: String, guidance: Option[String]): String =
    guidance.filter(_.nonEmpty).fold(purpose)(g => s"$purpose\n\n$g")
}
