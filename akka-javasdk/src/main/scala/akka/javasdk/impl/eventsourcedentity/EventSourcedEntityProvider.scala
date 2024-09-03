/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.eventsourcedentity

import akka.annotation.InternalApi
import akka.javasdk.eventsourcedentity.EventSourcedEntity
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext
import akka.javasdk.impl.ComponentDescriptor
import akka.javasdk.impl.ComponentDescriptorFactory
import akka.javasdk.impl.JsonMessageCodec
import com.google.protobuf.Descriptors

import java.util.function.Function

/**
 * INTERNAL API
 */
@InternalApi
private[impl] final case class EventSourcedEntityProvider[S, E, ES <: EventSourcedEntity[S, E]](
    entityClass: Class[ES],
    messageCodec: JsonMessageCodec,
    factory: Function[EventSourcedEntityContext, ES]) {
  private val typeId: String = ComponentDescriptorFactory.readComponentIdIdValue(entityClass)
  if (typeId == null)
    throw new IllegalArgumentException(
      "Event Sourced Entity [" + entityClass.getName + "] is missing '@TypeId' annotation")

  private val componentDescriptor = ComponentDescriptor.descriptorFor(entityClass, messageCodec)

  val serviceDescriptor: Descriptors.ServiceDescriptor = componentDescriptor.serviceDescriptor

  private def newRouter(context: EventSourcedEntityContext): EventSourcedEntityRouter[S, E, ES] = {
    val entity = factory.apply(context)
    new ReflectiveEventSourcedEntityRouter[S, E, ES](entity, componentDescriptor.commandHandlers, messageCodec)
  }

  def newServiceInstance(): EventSourcedEntityService = {
    new EventSourcedEntityService(
      newRouter,
      serviceDescriptor,
      Array[Descriptors.FileDescriptor](componentDescriptor.fileDescriptor),
      messageCodec,
      typeId,
      0)
  }

}
