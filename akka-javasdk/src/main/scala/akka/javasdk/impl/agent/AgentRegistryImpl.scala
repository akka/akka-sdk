/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import java.util.{ Set => JSet }

import scala.annotation.nowarn
import scala.jdk.CollectionConverters._

import akka.annotation.InternalApi
import akka.javasdk.agent.Agent
import akka.javasdk.agent.AgentRegistry
import akka.javasdk.agent.AgentRegistry.AgentInfo
import akka.javasdk.annotations.AgentDescription
import akka.javasdk.impl.reflection.Reflect
import akka.javasdk.impl.reflection.Reflect.Syntax.AnnotatedElementOps

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] object AgentRegistryImpl {
  final case class AgentDetails(id: String, name: String, description: String, role: String, agentClass: Class[Agent]) {
    def toAgentInfo: AgentInfo =
      new AgentInfo(id, name, description, role)
  }

  def agentDetailsFor[A <: Agent](agentClass: Class[A]): AgentRegistryImpl.AgentDetails = {

    @nowarn("cat=deprecation")
    val agentDescAnno = agentClass.annotationOption[AgentDescription]

    val agentRoleOptValue = Reflect.readAgentRole(agentClass)

    val componentId = Reflect.readComponentId(agentClass)
    val agentName =
      Reflect
        .readComponentName(agentClass)
        .orElse(agentDescAnno.map(_.name))
        .getOrElse(componentId)

    val agentDescription =
      Reflect
        .readComponentDescription(agentClass)
        .orElse(agentDescAnno.map(_.description))
        .getOrElse("")

    AgentRegistryImpl
      .AgentDetails(
        componentId,
        agentName,
        agentDescription,
        agentRoleOptValue.getOrElse(""),
        agentClass.asInstanceOf[Class[Agent]])

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
        "The agent id is defined with the @Component annotation."))
  }

}
