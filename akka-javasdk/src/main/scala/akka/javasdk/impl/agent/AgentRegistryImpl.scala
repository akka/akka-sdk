/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import java.util

import scala.jdk.CollectionConverters._

import akka.javasdk.agent.Agent
import akka.javasdk.agent.AgentRegistry
import akka.javasdk.impl.serialization.JsonSerializer

object AgentRegistryImpl {
  final case class AgentInfo(id: String, name: String, description: String, role: String, agentClass: Class[Agent]) {
    def hasRole(r: String): Boolean =
      role == r

    def toJsonValueObject: AgentJsonValueObject =
      AgentJsonValueObject(id, name, description)
  }

  final case class AgentJsonValueObject(id: String, name: String, description: String)
}

final class AgentRegistryImpl(agents: Set[AgentRegistryImpl.AgentInfo], serializer: JsonSerializer)
    extends AgentRegistry {
  private val agentsById = agents.map(a => a.id -> a).toMap

  val agentClassById: Map[String, Class[Agent]] = agents.iterator.map(a => a.id -> a.agentClass).toMap

  override def allAgentIds(): util.Set[String] =
    agents.iterator.map(_.id).toSet.asJava

  override def agentIdsWithRole(role: String): util.Set[String] =
    agents.iterator.collect { case a if a.hasRole(role) => a.id }.toSet.asJava

  override def agentDescriptionAsJson(agentId: String): String =
    agentsById.get(agentId) match {
      case Some(a) => serializer.toString(a.toJsonValueObject)
      case None =>
        throw new IllegalArgumentException(
          s"No agent with id [$agentId]. " +
          "The agent id is defined with the @ComponentId annotation.")
    }

}
