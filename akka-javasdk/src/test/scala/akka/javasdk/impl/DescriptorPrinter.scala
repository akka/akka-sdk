/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.javasdk.eventsourcedentity.TestEventSourcedEntity
import akka.javasdk.impl.ComponentDescriptor
import akka.javasdk.impl.JsonMessageCodec
import akka.javasdk.impl.ProtoDescriptorRenderer

import scala.reflect.ClassTag

/**
 * Utility class to quickly print descriptors
 */
object DescriptorPrinter {

  def descriptorFor[T](implicit ev: ClassTag[T]): ComponentDescriptor =
    ComponentDescriptor.descriptorFor(ev.runtimeClass, new JsonMessageCodec)

  def main(args: Array[String]) = {
    val descriptor = descriptorFor[TestEventSourcedEntity]
    println(ProtoDescriptorRenderer.toString(descriptor.fileDescriptor))
  }
}
