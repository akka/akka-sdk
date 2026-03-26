/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent.autonomous.capability

import java.util

import akka.annotation.InternalApi
import akka.javasdk.agent.Agent
import akka.javasdk.agent.autonomous.AutonomousAgent
import akka.javasdk.agent.autonomous.capability.Delegation

/**
 * INTERNAL API
 */
@InternalApi
final case class DelegationImpl(
    delegationTargets: util.List[Class[_ <: AutonomousAgent]],
    requestBasedTargets: util.List[Class[_ <: Agent]],
    maxParallel: Option[Int])
    extends Delegation {

  override def maxParallelWorkers(max: Int): Delegation =
    copy(maxParallel = Some(max))
}

/**
 * INTERNAL API
 */
@InternalApi
object DelegationImpl {
  def create(agents: Array[Class[_ <: AutonomousAgent]]): DelegationImpl =
    DelegationImpl(
      delegationTargets = util.List.of(agents: _*),
      requestBasedTargets = util.List.of(),
      maxParallel = None)

  def createRequestBased(agents: Array[Class[_ <: Agent]]): DelegationImpl =
    DelegationImpl(
      delegationTargets = util.List.of(),
      requestBasedTargets = util.List.of(agents: _*),
      maxParallel = None)
}
