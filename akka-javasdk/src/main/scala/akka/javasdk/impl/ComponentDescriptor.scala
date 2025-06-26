/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.annotation.InternalApi
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

  def apply(methods: Map[String, MethodInvoker]): ComponentDescriptor = {
    new ComponentDescriptor(methods)
  }
}

private[akka] final case class ComponentDescriptor private (methodInvokers: Map[String, MethodInvoker])
