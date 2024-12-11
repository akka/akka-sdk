/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.javasdk.impl.reflection.ActionHandlerMethod
import akka.javasdk.impl.reflection.KalixMethod
import akka.javasdk.impl.reflection.NameGenerator
import akka.annotation.InternalApi
import akka.javasdk.impl.ComponentDescriptorFactory.hasTimedActionEffectOutput
import akka.javasdk.impl.serialization.JsonSerializer

/**
 * INTERNAL API
 */
@InternalApi
private[impl] object TimedActionDescriptorFactory extends ComponentDescriptorFactory {

  override def buildDescriptorFor(
      component: Class[_],
      serializer: JsonSerializer,
      nameGenerator: NameGenerator): ComponentDescriptor = {

    val commandHandlerMethods = component.getDeclaredMethods
      .filter(hasTimedActionEffectOutput)
      .map { method =>
        val servMethod = ActionHandlerMethod(component, method)
        KalixMethod(servMethod, entityIds = Seq.empty)
      }
      .toIndexedSeq

    ComponentDescriptor(serializer, commandHandlerMethods)
  }
}
