/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.timedaction

import akka.annotation.InternalApi
import akka.javasdk.impl.ComponentDescriptor
import akka.javasdk.impl.JsonMessageCodec
import akka.javasdk.impl.action.ActionService
import akka.javasdk.timedaction.TimedAction
import com.google.protobuf.Descriptors

/**
 * INTERNAL API
 */
@InternalApi
private[impl] final case class TimedActionProvider[A <: TimedAction] private (
    cls: Class[A],
    messageCodec: JsonMessageCodec,
    factory: () => A) {
  private val componentDescriptor = ComponentDescriptor.descriptorFor(cls, messageCodec)

  val serviceDescriptor: Descriptors.ServiceDescriptor = componentDescriptor.serviceDescriptor

  private def newRouter(): TimedActionRouter[A] = {
    val action = factory()
    new ReflectiveTimedActionRouter[A](action, componentDescriptor.commandHandlers)
  }

  def newServiceInstance(): ActionService =
    new ActionService(newRouter _, serviceDescriptor, Array(componentDescriptor.fileDescriptor), messageCodec)
}
