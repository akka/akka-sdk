/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.consumer

import akka.annotation.InternalApi
import akka.javasdk.consumer.Consumer
import akka.javasdk.impl.ComponentDescriptor
import akka.javasdk.impl.ComponentDescriptorFactory
import akka.javasdk.impl.JsonMessageCodec
import com.google.protobuf.Descriptors

/**
 * INTERNAL API
 */
@InternalApi
private[impl] final case class ConsumerProvider[A <: Consumer](
    cls: Class[A],
    messageCodec: JsonMessageCodec,
    factory: () => A) {
  private val componentDescriptor = ComponentDescriptor.descriptorFor(cls, messageCodec)
  val serviceDescriptor: Descriptors.ServiceDescriptor = componentDescriptor.serviceDescriptor

  private def newRouter(): ConsumerRouter[A] = {
    val consumer: A = factory()
    new ReflectiveConsumerRouter[A](
      consumer,
      componentDescriptor.commandHandlers,
      ComponentDescriptorFactory.findIgnore(consumer.getClass))
  }

  def newServiceInstance(): ConsumerService =
    new ConsumerService(
      newRouter _,
      serviceDescriptor,
      Array[Descriptors.FileDescriptor](componentDescriptor.fileDescriptor),
      messageCodec)

}
