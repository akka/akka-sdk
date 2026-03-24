/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent.autonomous

import java.util
import java.util.function.UnaryOperator

import akka.annotation.InternalApi
import akka.javasdk.agent.autonomous.AgentSetup
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
final case class AgentSetupImpl(goal: Option[String], capabilities: util.List[AgentCapability]) extends AgentSetup {

  override def goal(goal: String): AgentSetup =
    copy(goal = Some(goal))

  override def canAcceptTask(task: TaskDefinition[_]): AgentSetup =
    copy(capabilities = appendCapability(TaskAcceptanceImpl.create(Array(task))))

  override def canAcceptTask(task: TaskDefinition[_], config: UnaryOperator[TaskAcceptance]): AgentSetup = {
    val initial: TaskAcceptance = TaskAcceptanceImpl.create(Array(task))
    val configured = config.apply(initial)
    copy(capabilities = appendCapability(configured.asInstanceOf[AgentCapability]))
  }

  override def canDelegateTo(agent: Class[_ <: AutonomousAgent]): AgentSetup =
    copy(capabilities = appendCapability(DelegationImpl.create(Array(agent))))

  override def canDelegateTo(agent: Class[_ <: AutonomousAgent], config: UnaryOperator[Delegation]): AgentSetup = {
    val initial: Delegation = DelegationImpl.create(Array(agent))
    val configured = config.apply(initial)
    copy(capabilities = appendCapability(configured.asInstanceOf[AgentCapability]))
  }

  override def canLeadTeam(config: UnaryOperator[TeamLeadership]): AgentSetup = {
    val initial: TeamLeadership = TeamLeadershipImpl(Seq.empty)
    val configured = config.apply(initial)
    copy(capabilities = appendCapability(configured.asInstanceOf[AgentCapability]))
  }

  private def appendCapability(capability: AgentCapability): util.List[AgentCapability] = {
    val result = new util.ArrayList[AgentCapability](capabilities)
    result.add(capability)
    util.Collections.unmodifiableList(result)
  }
}

/**
 * INTERNAL API
 */
@InternalApi
object AgentSetupImpl {
  def empty(): AgentSetupImpl =
    AgentSetupImpl(goal = None, capabilities = util.List.of())
}
