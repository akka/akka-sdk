/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent.autonomous

import java.util
import java.util.function.UnaryOperator

import akka.annotation.InternalApi
import akka.javasdk.agent.Guardrail
import akka.javasdk.agent.MemoryProvider
import akka.javasdk.agent.ModelProvider
import akka.javasdk.agent.RemoteMcpTools
import akka.javasdk.agent.autonomous.AgentDefinition
import akka.javasdk.agent.autonomous.AutonomousAgent
import akka.javasdk.agent.autonomous.capability.AgentCapability
import akka.javasdk.agent.autonomous.capability.Delegation
import akka.javasdk.agent.autonomous.capability.TaskAcceptance
import akka.javasdk.agent.autonomous.capability.TeamLeadership
import akka.javasdk.agent.task.TaskDefinition
import akka.javasdk.impl.agent.autonomous.capability.DelegationImpl
import akka.javasdk.impl.agent.autonomous.capability.TaskAcceptanceImpl
import akka.javasdk.impl.agent.autonomous.capability.TeamLeadershipImpl

/**
 * INTERNAL API
 */
@InternalApi
final case class AgentDefinitionImpl(
    goal: String,
    modelProvider: ModelProvider,
    toolInstancesOrClasses: util.List[AnyRef],
    mcpTools: util.List[RemoteMcpTools],
    requestGuardrailClassNames: util.List[String],
    responseGuardrailClassNames: util.List[String],
    memoryProvider: MemoryProvider,
    capabilities: util.List[AgentCapability])
    extends AgentDefinition {

  override def goal(goal: String): AgentDefinition =
    copy(goal = goal)

  override def canAcceptTask(task: TaskDefinition[_]): AgentDefinition =
    copy(capabilities = appendCapability(TaskAcceptanceImpl.create(Array(task))))

  override def canAcceptTask(task: TaskDefinition[_], config: UnaryOperator[TaskAcceptance]): AgentDefinition = {
    val initial: TaskAcceptance = TaskAcceptanceImpl.create(Array(task))
    val configured = config.apply(initial)
    copy(capabilities = appendCapability(configured.asInstanceOf[AgentCapability]))
  }

  override def canDelegateTo(agent: Class[_ <: AutonomousAgent]): AgentDefinition =
    copy(capabilities = appendCapability(DelegationImpl.create(Array(agent))))

  override def canDelegateTo(agent: Class[_ <: AutonomousAgent], config: UnaryOperator[Delegation]): AgentDefinition = {
    val initial: Delegation = DelegationImpl.create(Array(agent))
    val configured = config.apply(initial)
    copy(capabilities = appendCapability(configured.asInstanceOf[AgentCapability]))
  }

  override def canLeadTeam(config: UnaryOperator[TeamLeadership]): AgentDefinition = {
    val initial: TeamLeadership = TeamLeadershipImpl(Seq.empty)
    val configured = config.apply(initial)
    copy(capabilities = appendCapability(configured.asInstanceOf[AgentCapability]))
  }

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

  override def memory(memory: MemoryProvider): AgentDefinition =
    copy(memoryProvider = memory)

  private def appendCapability(capability: AgentCapability): util.List[AgentCapability] = {
    val result = new util.ArrayList[AgentCapability](capabilities)
    result.add(capability)
    util.Collections.unmodifiableList(result)
  }

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
      goal = "",
      modelProvider = null,
      toolInstancesOrClasses = util.List.of(),
      mcpTools = util.List.of(),
      requestGuardrailClassNames = util.List.of(),
      responseGuardrailClassNames = util.List.of(),
      memoryProvider = MemoryProvider.fromConfig(),
      capabilities = util.List.of())
}
