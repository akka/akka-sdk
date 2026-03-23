/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent.autonomous.capability

import java.util

import akka.annotation.InternalApi
import akka.javasdk.agent.autonomous.AutonomousAgent

/**
 * INTERNAL API
 */
@InternalApi
final case class DelegationImpl(delegationTargets: util.List[Class[_ <: AutonomousAgent]], maxParallel: Option[Int])
    extends AgentCapability

/**
 * INTERNAL API
 */
@InternalApi
object DelegationImpl {
  def create(agents: Array[Class[_ <: AutonomousAgent]]): DelegationImpl =
    DelegationImpl(delegationTargets = util.List.of(agents: _*), maxParallel = None)
}
