/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import java.util.{ Set => JSet }

import scala.jdk.CollectionConverters._

import akka.annotation.InternalApi
import akka.javasdk.agent.Agent
import akka.javasdk.agent.AgentRegistry
import akka.javasdk.agent.AgentRegistry.AgentInfo

/**
 * INTERNAL API
 */
@InternalApi private[javasdk] object AgentRegistryImpl {
  final case class AgentDetails(id: String, name: String, description: String, role: String, agentClass: Class[Agent]) {
    def toAgentInfo: AgentInfo =
      new AgentInfo(id, name, description, role)
  }

}

/**
 * INTERNAL API
 */
@InternalApi private[javasdk] final class AgentRegistryImpl(agents: Set[AgentRegistryImpl.AgentDetails])
    extends AgentRegistry {
  private val agentInfoById = agents.map(a => a.id -> a.toAgentInfo).toMap

  val agentClassById: Map[String, Class[Agent]] = agents.iterator.map(a => a.id -> a.agentClass).toMap

  override def allAgents(): JSet[AgentInfo] =
    agentInfoById.values.toSet.asJava

  override def agentsWithRole(role: String): JSet[AgentInfo] =
    agentInfoById.values.iterator.filter(_.hasRole(role)).toSet.asJava

  override def agentInfo(agentId: String): AgentInfo = {
    agentInfoById.getOrElse(
      agentId,
      throw new IllegalArgumentException(
        s"No agent with id [$agentId]. " +
        "The agent id is defined with the @ComponentId annotation."))
  }

}
