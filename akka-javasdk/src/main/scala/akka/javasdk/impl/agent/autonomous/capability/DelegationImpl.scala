/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent.autonomous.capability

import java.util

import scala.jdk.CollectionConverters._

import akka.annotation.InternalApi
import akka.javasdk.agent.Agent
import akka.javasdk.agent.AgentDelegationWorker
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
  def create(first: Class[_ <: AgentDelegationWorker], rest: Array[Class[_ <: AgentDelegationWorker]]): DelegationImpl =
    fromSeq(first +: rest.toSeq)

  // Used internally by tests that need an empty delegation
  private[javasdk] def create(agents: Array[Class[_ <: AgentDelegationWorker]]): DelegationImpl =
    fromSeq(agents.toSeq)

  private def fromSeq(agents: Seq[Class[_ <: AgentDelegationWorker]]): DelegationImpl = {
    // Partitions targets by type. If both types are mixed in one Delegation.to() call they end up
    // in the same DelegationGroup and share maxParallelWorkers, which may have unclear semantics
    // since autonomous agents run task lifecycles while request-based agents are single calls.
    val (autonomous, requestBased) = agents.partition(classOf[AutonomousAgent].isAssignableFrom)
    DelegationImpl(
      delegationTargets = util.List.copyOf(autonomous.map(_.asInstanceOf[Class[_ <: AutonomousAgent]]).asJava),
      requestBasedTargets = util.List.copyOf(requestBased.map(_.asInstanceOf[Class[_ <: Agent]]).asJava),
      maxParallel = None)
  }
}
