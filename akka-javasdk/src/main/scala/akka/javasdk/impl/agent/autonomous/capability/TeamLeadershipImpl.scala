/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent.autonomous.capability

import java.util.function.UnaryOperator

import akka.annotation.InternalApi
import akka.javasdk.agent.autonomous.AutonomousAgent
import akka.javasdk.agent.autonomous.capability.TeamLeadership

/**
 * INTERNAL API
 */
@InternalApi
final case class TeamLeadershipImpl(members: Seq[MemberTypeImpl]) extends TeamLeadership {

  override def withMember(agentClass: Class[_ <: AutonomousAgent]): TeamLeadership =
    copy(members = members :+ MemberTypeImpl(agentClass, 1))

  override def withMember(
      agentClass: Class[_ <: AutonomousAgent],
      config: UnaryOperator[TeamLeadership.MemberConfig]): TeamLeadership = {
    val initial: TeamLeadership.MemberConfig = MemberTypeImpl(agentClass, 1)
    val configured = config.apply(initial).asInstanceOf[MemberTypeImpl]
    copy(members = members :+ configured)
  }
}

/**
 * INTERNAL API
 */
@InternalApi
final case class MemberTypeImpl(agentClass: Class[_ <: AutonomousAgent], maxMemberInstances: Int)
    extends TeamLeadership.MemberConfig {

  override def maxInstances(max: Int): TeamLeadership.MemberConfig =
    copy(maxMemberInstances = max)
}
