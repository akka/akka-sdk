/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import scala.reflect.ClassTag

import akka.javasdk.eventsourcedentity.TestEventSourcedEntity
import akka.javasdk.impl.serialization.JsonSerializer

/**
 * Utility class to quickly print descriptors
 */
object DescriptorPrinter {

  def descriptorFor[T](implicit ev: ClassTag[T]): ComponentDescriptor =
    ComponentDescriptor.descriptorFor(ev.runtimeClass, new JsonSerializer)

  def main(args: Array[String]) = {
    val descriptor = descriptorFor[TestEventSourcedEntity]
    println(ProtoDescriptorRenderer.toString(descriptor.fileDescriptor))
  }
}
