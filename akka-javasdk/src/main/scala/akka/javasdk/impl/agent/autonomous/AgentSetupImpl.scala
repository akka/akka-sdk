/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent.autonomous

import java.util

import akka.annotation.InternalApi
import akka.javasdk.agent.autonomous.AgentSetup
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
final case class AgentSetupImpl(
    goal: Option[String],
    capabilities: util.List[AgentCapability],
    pendingCapability: Option[AgentCapability])
    extends AgentSetup
    with AgentSetup.TaskAcceptanceBuilder
    with AgentSetup.DelegationBuilder
    with AgentSetup.TeamBuilder
    with AgentSetup.TeamMemberBuilder {

  // -- General config methods: finalize pending first, return AgentSetup --

  override def goal(goal: String): AgentSetup =
    finalizePending().copy(goal = Some(goal))

  // -- Capability entry points: finalize pending, start new pending --

  override def canAcceptTasks(tasks: TaskDefinition[_]*): AgentSetup.TaskAcceptanceBuilder =
    finalizePending().copy(pendingCapability = Some(TaskAcceptanceImpl.create(tasks.toArray)))

  override def canHandoffTo(agent: Class[_ <: AutonomousAgent]): AgentSetup =
    finalizePending().copy(pendingCapability = Some(HandoffImpl(agent))).finalizePending()

  override def canDelegateTo(agent: Class[_ <: AutonomousAgent]): AgentSetup.DelegationBuilder =
    finalizePending().copy(pendingCapability = Some(DelegationImpl.create(Array(agent))))

  override def canLeadTeam(): AgentSetup.TeamBuilder =
    finalizePending().copy(pendingCapability = Some(TeamLeadershipImpl(Seq.empty)))

  // -- TaskAcceptanceBuilder modifiers --

  override def maxIterationsPerTask(max: Int): AgentSetup.TaskAcceptanceBuilder =
    copy(pendingCapability = pendingCapability.map {
      case ta: TaskAcceptanceImpl => ta.copy(maxIterations = Some(max))
      case other                  => other
    })

  // -- DelegationBuilder modifiers --

  override def maxParallelWorkers(max: Int): AgentSetup.DelegationBuilder =
    copy(pendingCapability = pendingCapability.map {
      case d: DelegationImpl => d.copy(maxParallel = Some(max))
      case other             => other
    })

  // -- TeamBuilder / TeamMemberBuilder --

  override def withMember(agentClass: Class[_ <: AutonomousAgent]): AgentSetup.TeamMemberBuilder =
    copy(pendingCapability = pendingCapability.map {
      case tl: TeamLeadershipImpl => tl.copy(members = tl.members :+ MemberTypeImpl(agentClass, 1))
      case other                  => other
    })

  override def maxInstances(max: Int): AgentSetup.TeamMemberBuilder =
    copy(pendingCapability = pendingCapability.map {
      case tl: TeamLeadershipImpl if tl.members.nonEmpty =>
        val updated = tl.members.init :+ tl.members.last.copy(maxMemberInstances = max)
        tl.copy(members = updated)
      case other => other
    })

  // -- Internal --

  private def finalizePending(): AgentSetupImpl =
    pendingCapability match {
      case Some(cap) =>
        copy(capabilities = appendTo(capabilities, cap), pendingCapability = None)
      case None => this
    }

  /** Called by the framework before consuming the setup. */
  def build(): AgentSetupImpl = finalizePending()

  private def appendTo[T](list: util.List[T], item: T): util.List[T] = {
    val result = new util.ArrayList[T](list)
    result.add(item)
    util.Collections.unmodifiableList(result)
  }
}

/**
 * INTERNAL API
 */
@InternalApi
object AgentSetupImpl {
  def empty(): AgentSetupImpl =
    AgentSetupImpl(goal = None, capabilities = util.List.of(), pendingCapability = None)
}
