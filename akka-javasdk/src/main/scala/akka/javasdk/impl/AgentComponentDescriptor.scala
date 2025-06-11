/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.annotation.InternalApi
import akka.javasdk.impl.agent.FunctionTools
import akka.javasdk.impl.agent.ToolDescriptors
import akka.javasdk.impl.serialization.JsonSerializer
import akka.runtime.sdk.spi.SpiAgent

/**
 * INTERNAL API
 */
@InternalApi
private[impl] object AgentComponentDescriptor {

  def descriptorFor(agentClass: Class[_], serializer: JsonSerializer): AgentComponentDescriptor = {
    AgentComponentDescriptor(
      ComponentDescriptor.descriptorFor(agentClass, serializer),
      ToolDescriptors(agentClass),
      FunctionTools(agentClass))
  }
}

private[akka] final case class AgentComponentDescriptor(
    componentDescriptor: ComponentDescriptor,
    functionDescriptors: Seq[SpiAgent.ToolDescriptor],
    functionTools: Map[String, FunctionTools.FunctionToolInvoker]) {}
