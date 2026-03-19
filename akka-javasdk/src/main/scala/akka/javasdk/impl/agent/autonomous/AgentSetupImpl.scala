/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent.autonomous

import java.util

import akka.annotation.InternalApi
import akka.javasdk.agent.autonomous.AgentSetup
import akka.javasdk.agent.autonomous.capability.AgentCapability

/**
 * INTERNAL API
 */
@InternalApi
final case class AgentSetupImpl(goal: Option[String], capabilities: util.List[AgentCapability]) extends AgentSetup {

  override def goal(goal: String): AgentSetup =
    copy(goal = Some(goal))

  override def capabilities(capabilities: AgentCapability*): AgentSetup = {
    val result = new util.ArrayList[AgentCapability](this.capabilities)
    capabilities.foreach(result.add)
    copy(capabilities = util.Collections.unmodifiableList(result))
  }
}

/**
 * INTERNAL API
 */
@InternalApi
object AgentSetupImpl {
  def empty(): AgentSetupImpl =
    AgentSetupImpl(goal = None, capabilities = util.List.of())
}
