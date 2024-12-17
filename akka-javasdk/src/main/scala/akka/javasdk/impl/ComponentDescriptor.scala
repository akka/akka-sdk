/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.annotation.InternalApi
import akka.javasdk.impl.reflection.KalixMethod
import akka.javasdk.impl.serialization.JsonSerializer

/**
 * The component descriptor is used the reflective routers routing incoming calls to the right method of the user
 * component class.
 *
 * INTERNAL API
 */
@InternalApi
private[impl] object ComponentDescriptor {

  def descriptorFor(component: Class[_], serializer: JsonSerializer): ComponentDescriptor =
    ComponentDescriptorFactory.getFactoryFor(component).buildDescriptorFor(component, serializer)

  def apply(serializer: JsonSerializer, kalixMethods: Seq[KalixMethod]): ComponentDescriptor = {

    //TODO remove capitalization of method name, can't be done per component, because component client reuse the same logic for all
    val methods: Map[String, CommandHandler] =
      kalixMethods.map { method =>
        (method.serviceMethod.methodName.capitalize, method.toCommandHandler(serializer))
      }.toMap

    new ComponentDescriptor(methods)

  }

  def apply(methods: Map[String, CommandHandler]): ComponentDescriptor = {
    new ComponentDescriptor(methods)
  }
}

private[akka] final case class ComponentDescriptor private (commandHandlers: Map[String, CommandHandler])
