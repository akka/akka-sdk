/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import java.util

import scala.jdk.CollectionConverters._

import akka.annotation.InternalApi
import akka.javasdk.agent.Agent
import akka.javasdk.agent.AgentRegistry
import akka.javasdk.agent.AgentRegistry.AgentInfo
import akka.javasdk.impl.serialization.JsonSerializer

/**
 * INTERNAL API
 */
@InternalApi private[javasdk] object AgentRegistryImpl {
  final case class AgentDetails(id: String, name: String, description: String, role: String, agentClass: Class[Agent]) {
    def hasRole(r: String): Boolean =
      role == r

    def toAgentInfo: AgentInfo =
      new AgentInfo(id, name, description, role)
  }

}

/**
 * INTERNAL API
 */
@InternalApi private[javasdk] final class AgentRegistryImpl(
    agents: Set[AgentRegistryImpl.AgentDetails],
    serializer: JsonSerializer)
    extends AgentRegistry {
  private val agentsById = agents.map(a => a.id -> a).toMap

  val agentClassById: Map[String, Class[Agent]] = agents.iterator.map(a => a.id -> a.agentClass).toMap

  override def allAgentIds(): util.Set[String] =
    agents.iterator.map(_.id).toSet.asJava

  override def agentIdsWithRole(role: String): util.Set[String] =
    agents.iterator.collect { case a if a.hasRole(role) => a.id }.toSet.asJava

  override def agentInfo(agentId: String): AgentInfo = {
    agentsById.get(agentId) match {
      case Some(a) => a.toAgentInfo
      case None =>
        throw new IllegalArgumentException(
          s"No agent with id [$agentId]. " +
          "The agent id is defined with the @ComponentId annotation.")
    }
  }

  override def agentInfoAsJson(agentId: String): String =
    serializer.toJsonString(agentInfo(agentId))
}
