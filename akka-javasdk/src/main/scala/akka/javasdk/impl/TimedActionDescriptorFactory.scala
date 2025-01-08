/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.annotation.InternalApi
import akka.javasdk.impl.reflection.Reflect.isCommandHandlerCandidate
import akka.javasdk.impl.serialization.JsonSerializer
import akka.javasdk.timedaction.TimedAction

/**
 * INTERNAL API
 */
@InternalApi
private[impl] object TimedActionDescriptorFactory extends ComponentDescriptorFactory {

  override def buildDescriptorFor(component: Class[_], serializer: JsonSerializer): ComponentDescriptor = {
    //TODO remove capitalization of method name, can't be done per component, because component client reuse the same logic for all
    val invokers = component.getDeclaredMethods.collect {
      case method if isCommandHandlerCandidate[TimedAction.Effect](method) =>
        method.getName.capitalize -> MethodInvoker(method)
    }.toMap

    ComponentDescriptor(invokers)
  }
}
