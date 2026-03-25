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
final case class TeamLeadershipImpl(members: Seq[TeamMemberImpl]) extends TeamLeadership

/**
 * INTERNAL API
 */
@InternalApi
final case class TeamMemberImpl(agentClass: Class[_ <: AutonomousAgent], maxMemberInstances: Int)
    extends TeamLeadership.TeamMember {

  override def maxInstances(max: Int): TeamLeadership.TeamMember =
    copy(maxMemberInstances = max)
}

/**
 * INTERNAL API
 */
@InternalApi
object TeamLeadershipImpl {
  def create(first: TeamLeadership.TeamMember, rest: Array[TeamLeadership.TeamMember]): TeamLeadershipImpl =
    TeamLeadershipImpl((first +: rest.toSeq).map(_.asInstanceOf[TeamMemberImpl]))
}
