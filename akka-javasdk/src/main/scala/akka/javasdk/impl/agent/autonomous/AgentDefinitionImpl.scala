/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent.autonomous

import java.util

import akka.annotation.InternalApi
import akka.javasdk.agent.Guardrail
import akka.javasdk.agent.MemoryProvider
import akka.javasdk.agent.ModelProvider
import akka.javasdk.agent.RemoteMcpTools
import akka.javasdk.agent.autonomous.AgentDefinition
import akka.javasdk.agent.autonomous.AutonomousAgent
import akka.javasdk.impl.agent.autonomous.capability.AgentCapability
import akka.javasdk.agent.task.TaskDefinition
import akka.javasdk.impl.agent.autonomous.capability.DelegationImpl
import akka.javasdk.impl.agent.autonomous.capability.HandoffImpl
import akka.javasdk.impl.agent.autonomous.capability.MemberTypeImpl
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
    capabilities: util.List[AgentCapability],
    pendingCapability: Option[AgentCapability])
    extends AgentDefinition
    with AutonomousAgent.TaskAcceptanceBuilder
    with AutonomousAgent.DelegationBuilder
    with AutonomousAgent.TeamBuilder
    with AutonomousAgent.TeamMemberBuilder {

  // -- General config methods: finalize pending first, return AgentDefinition --

  override def goal(goal: String): AgentDefinition =
    finalizePending().copy(goal = goal)

  override def modelProvider(provider: ModelProvider): AgentDefinition =
    finalizePending().copy(modelProvider = provider)

  override def tools(tools: AnyRef*): AgentDefinition =
    finalizePending().copy(toolInstancesOrClasses = concat(toolInstancesOrClasses, tools))

  override def mcpTools(tools: RemoteMcpTools*): AgentDefinition =
    finalizePending().copy(mcpTools = concat(mcpTools, tools))

  override def requestGuardrails(guardrails: Class[_ <: Guardrail]*): AgentDefinition =
    finalizePending().copy(requestGuardrailClassNames = concat(requestGuardrailClassNames, guardrails.map(_.getName)))

  override def responseGuardrails(guardrails: Class[_ <: Guardrail]*): AgentDefinition =
    finalizePending().copy(responseGuardrailClassNames = concat(responseGuardrailClassNames, guardrails.map(_.getName)))

  override def memory(memory: MemoryProvider): AgentDefinition =
    finalizePending().copy(memoryProvider = memory)

  // -- Capability entry points: finalize pending, start new pending --

  override def canAcceptTasks(tasks: TaskDefinition[_]*): AutonomousAgent.TaskAcceptanceBuilder =
    finalizePending().copy(pendingCapability = Some(TaskAcceptanceImpl.create(tasks.toArray)))

  override def canHandoffTo(agent: Class[_ <: AutonomousAgent]): AgentDefinition =
    finalizePending().copy(pendingCapability = Some(HandoffImpl(agent))).finalizePending()

  override def canDelegateTo(agent: Class[_ <: AutonomousAgent]): AutonomousAgent.DelegationBuilder =
    finalizePending().copy(pendingCapability = Some(DelegationImpl.create(Array(agent))))

  override def canLeadTeam(): AutonomousAgent.TeamBuilder =
    finalizePending().copy(pendingCapability = Some(TeamLeadershipImpl(Seq.empty)))

  // -- TaskAcceptanceBuilder modifiers --

  override def maxIterationsPerTask(max: Int): AutonomousAgent.TaskAcceptanceBuilder =
    copy(pendingCapability = pendingCapability.map {
      case ta: TaskAcceptanceImpl => ta.copy(maxIterations = Some(max))
      case other                  => other
    })

  // -- DelegationBuilder modifiers --

  override def maxParallelWorkers(max: Int): AutonomousAgent.DelegationBuilder =
    copy(pendingCapability = pendingCapability.map {
      case d: DelegationImpl => d.copy(maxParallel = Some(max))
      case other             => other
    })

  // -- TeamBuilder / TeamMemberBuilder --

  override def withMember(agentClass: Class[_ <: AutonomousAgent]): AutonomousAgent.TeamMemberBuilder =
    copy(pendingCapability = pendingCapability.map {
      case tl: TeamLeadershipImpl => tl.copy(members = tl.members :+ MemberTypeImpl(agentClass, 1))
      case other                  => other
    })

  override def maxInstances(max: Int): AutonomousAgent.TeamMemberBuilder =
    copy(pendingCapability = pendingCapability.map {
      case tl: TeamLeadershipImpl if tl.members.nonEmpty =>
        val updated = tl.members.init :+ tl.members.last.copy(maxMemberInstances = max)
        tl.copy(members = updated)
      case other => other
    })

  // -- Internal --

  private def finalizePending(): AgentDefinitionImpl =
    pendingCapability match {
      case Some(cap) =>
        copy(capabilities = appendTo(capabilities, cap), pendingCapability = None)
      case None => this
    }

  /** Called by the framework before consuming the definition. */
  def build(): AgentDefinitionImpl = finalizePending()

  private def appendTo[T](list: util.List[T], item: T): util.List[T] = {
    val result = new util.ArrayList[T](list)
    result.add(item)
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
      capabilities = util.List.of(),
      pendingCapability = None)
}
