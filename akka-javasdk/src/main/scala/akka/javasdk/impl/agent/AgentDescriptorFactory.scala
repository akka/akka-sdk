/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import akka.annotation.InternalApi
import akka.javasdk.agent.Agent
import akka.javasdk.impl.ComponentDescriptor
import akka.javasdk.impl.ComponentDescriptorFactory
import akka.javasdk.impl.MethodInvoker
import akka.javasdk.impl.reflection.Reflect.isCommandHandlerCandidate
import akka.javasdk.impl.serialization.JsonSerializer

/**
 * INTERNAL API
 */
@InternalApi
private[impl] object AgentDescriptorFactory extends ComponentDescriptorFactory {

  override def buildDescriptorFor(component: Class[_], serializer: JsonSerializer): ComponentDescriptor = {
    //TODO remove capitalization of method name, can't be done per component, because component client reuse the same logic for all
    val commandHandlerMethods = if (classOf[Agent].isAssignableFrom(component)) {
      component.getDeclaredMethods.collect {
        case method
            if isCommandHandlerCandidate[Agent.Effect[_]](method) || isCommandHandlerCandidate[Agent.StreamEffect](
              method) =>
          method.getName.capitalize -> MethodInvoker(method)
      }
    } else {

      // should never happen
      throw new RuntimeException(s"Unsupported component type: ${component.getName}. Supported types are: Agent")
    }

    ComponentDescriptor(commandHandlerMethods.toMap)
  }
}
