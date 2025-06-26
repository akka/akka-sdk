/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import akka.annotation.InternalApi
import akka.javasdk.agent.Agent
import akka.javasdk.agent.AgentContext
import akka.javasdk.impl.CommandSerialization
import akka.javasdk.impl.HandlerNotFoundException
import akka.javasdk.impl.MethodInvoker
import akka.javasdk.impl.serialization.JsonSerializer
import akka.runtime.sdk.spi.BytesPayload

import java.util.Optional

/**
 * INTERNAL API
 */
@InternalApi
private[impl] class ReflectiveAgentRouter[A <: Agent](
    val factory: AgentContext => A,
    methodInvokers: Map[String, MethodInvoker],
    serializer: JsonSerializer) {

  private def methodInvokerLookup(commandName: String, agent: A): MethodInvoker =
    methodInvokers.get(commandName) match {
      case Some(handler) => handler
      case None =>
        throw new HandlerNotFoundException("command", commandName, agent.getClass, methodInvokers.keySet)
    }

  /**
   * Return type is `Agent.Effect` or `Agent.StreamEffect`S
   */
  def handleCommand(agent: A, commandName: String, command: BytesPayload, context: AgentContext): AnyRef = {

    agent._internalSetContext(Optional.of(context))

    val methodInvoker = methodInvokerLookup(commandName, agent)

    if (command.isEmpty && methodInvoker.method.getParameterCount > 0) {
      throw new IllegalArgumentException(
        s"Command handler method [$commandName] on [${agent.getClass.getName}] requires a parameter, " +
        s"but was invoked without parameter. If you are using dynamicMethod, invoke it with a parameter.")
    } else if (command.nonEmpty && methodInvoker.method.getParameterCount == 0) {
      throw new IllegalArgumentException(
        s"Command handler method [$commandName] on [${agent.getClass.getName}] doesn't take a parameter, " +
        s"but was invoked with parameter. If you are using dynamicMethod, invoke it without a parameter.")
    } else if (serializer.isJson(command) || command.isEmpty) {
      // - BytesPayload.empty - there is no real command, and we are calling a method with arity 0
      // - BytesPayload with json - we deserialize it and call the method
      val deserializedCommand =
        CommandSerialization.deserializeComponentClientCommand(methodInvoker.method, command, serializer)

      val result = deserializedCommand match {
        case None          => methodInvoker.invoke(agent)
        case Some(command) => methodInvoker.invokeDirectly(agent, command)
      }
      result
    } else {
      throw new IllegalStateException(
        s"Could not find a matching command handler for method [$commandName], content type [${command.contentType}] " +
        s"on [${agent.getClass.getName}]")
    }
  }

}
