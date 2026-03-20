/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent.autonomous.capability

import akka.annotation.InternalApi
import akka.javasdk.agent.autonomous.AutonomousAgent
import akka.javasdk.agent.autonomous.capability.TeamLeadership

/**
 * INTERNAL API
 */
@InternalApi
final case class TeamLeadershipImpl(members: Seq[MemberTypeImpl]) extends TeamLeadership

/**
 * INTERNAL API
 */
@InternalApi
final case class MemberTypeImpl(agentClass: Class[_ <: AutonomousAgent], maxMemberInstances: Int)
    extends TeamLeadership.MemberType {

  override def maxInstances(max: Int): TeamLeadership.MemberType =
    copy(maxMemberInstances = max)
}

/**
 * INTERNAL API
 */
@InternalApi
object TeamLeadershipImpl {
  def create(members: Array[TeamLeadership.MemberType]): TeamLeadershipImpl =
    TeamLeadershipImpl(members.toSeq.map(_.asInstanceOf[MemberTypeImpl]))
}
