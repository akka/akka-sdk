/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.keyvalueentity

import akka.annotation.InternalApi
import akka.javasdk.impl.ComponentDescriptor
import akka.javasdk.impl.ComponentDescriptorFactory
import akka.javasdk.impl.JsonMessageCodec
import akka.javasdk.keyvalueentity.KeyValueEntity
import akka.javasdk.keyvalueentity.KeyValueEntityContext
import com.google.protobuf.Descriptors

import java.util.function.Function

/**
 * INTERNAL API
 */
@InternalApi
private[impl] final case class KeyValueEntityProvider[S, E <: KeyValueEntity[S]](
    entityClass: Class[E],
    messageCodec: JsonMessageCodec,
    factory: Function[KeyValueEntityContext, E]) {
  private val typeId: String = ComponentDescriptorFactory.readComponentIdIdValue(entityClass)
  if (typeId eq null)
    throw new IllegalArgumentException(
      "Key Value Entity [" + entityClass.getName + "] is missing '@ComponentId' annotation")
  private val componentDescriptor = ComponentDescriptor.descriptorFor(entityClass, messageCodec)

  val serviceDescriptor: Descriptors.ServiceDescriptor = componentDescriptor.serviceDescriptor

  private def newRouter(context: KeyValueEntityContext): KeyValueEntityRouter[S, E] = {
    val entity = factory.apply(context)
    new ReflectiveKeyValueEntityRouter[S, E](entity, componentDescriptor.commandHandlers)
  }

  def newServiceInstance(): KeyValueEntityService = {
    new KeyValueEntityService(
      newRouter,
      serviceDescriptor,
      Array[Descriptors.FileDescriptor](componentDescriptor.fileDescriptor),
      messageCodec,
      typeId)
  }

}
