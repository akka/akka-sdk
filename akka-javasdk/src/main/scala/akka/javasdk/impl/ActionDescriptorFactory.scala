/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.javasdk.impl.reflection.ActionHandlerMethod
import akka.javasdk.impl.reflection.KalixMethod
import akka.javasdk.impl.reflection.NameGenerator
import ComponentDescriptorFactory.hasActionOutput
import akka.javasdk

private[impl] object ActionDescriptorFactory extends ComponentDescriptorFactory {

  override def buildDescriptorFor(
      component: Class[_],
      messageCodec: JsonMessageCodec,
      nameGenerator: NameGenerator): ComponentDescriptor = {

    val serviceName = nameGenerator.getName(component.getSimpleName)

    val commandHandlerMethods = component.getDeclaredMethods
      .filter(hasActionOutput)
      .map { method =>
        val servMethod = ActionHandlerMethod(component, method)
        KalixMethod(servMethod, entityIds = Seq.empty)
      }
      .toIndexedSeq

    javasdk.impl.ComponentDescriptor(
      nameGenerator,
      messageCodec,
      serviceName,
      serviceOptions = None,
      component.getPackageName,
      commandHandlerMethods)
  }
}
