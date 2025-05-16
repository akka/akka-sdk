/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import akka.annotation.InternalApi
import akka.javasdk.agent.ChatAgent
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
    val commandHandlerMethods = if (classOf[ChatAgent].isAssignableFrom(component)) {
      component.getDeclaredMethods.collect {
        case method if isCommandHandlerCandidate[ChatAgent.Effect[_]](method) =>
          method.getName.capitalize -> MethodInvoker(method)
      }
    } else {

      // should never happen
      throw new RuntimeException(s"Unsupported component type: ${component.getName}. Supported types are: ChatAgent")
    }

    ComponentDescriptor(commandHandlerMethods.toMap)
  }
}
