/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import java.util.Optional

import scala.jdk.OptionConverters._

import akka.annotation.InternalApi
import akka.javasdk.agent.Agent
import akka.javasdk.agent.Delegative.Delegation
import akka.javasdk.impl.reflection.Reflect

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] object DelegationImpl {
  def toAgent[A <: Agent](agentClass: Class[A]): Delegation = {
    val componentId = Reflect.readComponentId(agentClass)
    val description = Reflect.readAgentDescription(agentClass).toJava
    if (Reflect.isDelegativeAgent(agentClass))
      DelegativeAgentDelegation(componentId, description)
    else
      AgentDelegation(componentId, description)
  }
}

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] final case class AgentDelegation(agentComponentId: String, description: Optional[String])
    extends Delegation {
  override def withDescription(description: String): Delegation =
    copy(description = Optional.ofNullable(description))
}

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] final case class DelegativeAgentDelegation(agentComponentId: String, description: Optional[String])
    extends Delegation {
  override def withDescription(description: String): Delegation =
    copy(description = Optional.ofNullable(description))
}
