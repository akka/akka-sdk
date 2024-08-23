/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.impl

import akka.platform.javasdk.impl
import akka.platform.javasdk.impl.ComponentDescriptorFactory.hasActionOutput
import akka.platform.javasdk.impl.reflection.ActionHandlerMethod
import akka.platform.javasdk.impl.reflection.KalixMethod
import akka.platform.javasdk.impl.reflection.NameGenerator

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

    impl.ComponentDescriptor(
      nameGenerator,
      messageCodec,
      serviceName,
      serviceOptions = None,
      component.getPackageName,
      commandHandlerMethods)
  }
}
