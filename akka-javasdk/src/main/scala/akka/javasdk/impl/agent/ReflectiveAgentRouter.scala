/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import java.util.Optional

import akka.annotation.InternalApi
import akka.javasdk.agent.Agent
import akka.javasdk.agent.AgentContext
import akka.javasdk.impl.CommandSerialization
import akka.javasdk.impl.HandlerNotFoundException
import akka.javasdk.impl.MethodInvoker
import akka.javasdk.impl.serialization.JsonSerializer
import akka.runtime.sdk.spi.BytesPayload

/**
 * INTERNAL API
 */
@InternalApi
private[impl] class ReflectiveAgentRouter(
    val agent: Agent,
    methodInvokers: Map[String, MethodInvoker],
    serializer: JsonSerializer) {

  private def methodInvokerLookup(commandName: String): MethodInvoker =
    methodInvokers.get(commandName) match {
      case Some(handler) => handler
      case None =>
        throw new HandlerNotFoundException("command", commandName, agent.getClass, methodInvokers.keySet)
    }

  def handleCommand(commandName: String, command: BytesPayload, context: AgentContext): Agent.Effect[_] = {
    // only set, never cleared, to allow access from other threads in async callbacks in the consumer
    // the same handler and consumer instance is expected to only ever be invoked for a single message
    agent._internalSetContext(Optional.of(context))

    val methodInvoker = methodInvokerLookup(commandName)

    if (serializer.isJson(command) || command.isEmpty) {
      // - BytesPayload.empty - there is no real command, and we are calling a method with arity 0
      // - BytesPayload with json - we deserialize it and call the method
      val deserializedCommand =
        CommandSerialization.deserializeComponentClientCommand(methodInvoker.method, command, serializer)
      val result = deserializedCommand match {
        case None          => methodInvoker.invoke(agent)
        case Some(command) => methodInvoker.invokeDirectly(agent, command)
      }
      result.asInstanceOf[Agent.Effect[_]]
    } else {
      throw new IllegalStateException(
        s"Could not find a matching command handler for method [$commandName], content type [${command.contentType}] " +
        s"on [${agent.getClass.getName}]")
    }
  }

}
