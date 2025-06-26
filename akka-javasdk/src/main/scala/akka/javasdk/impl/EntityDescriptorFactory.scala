/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.annotation.InternalApi
import akka.javasdk.eventsourcedentity.EventSourcedEntity
import akka.javasdk.impl.reflection.Reflect.isCommandHandlerCandidate
import akka.javasdk.impl.serialization.JsonSerializer
import akka.javasdk.keyvalueentity.KeyValueEntity
import akka.javasdk.workflow.Workflow

/**
 * INTERNAL API
 */
@InternalApi
private[impl] object EntityDescriptorFactory extends ComponentDescriptorFactory {

  override def buildDescriptorFor(component: Class[_], serializer: JsonSerializer): ComponentDescriptor = {
    //TODO remove capitalization of method name, can't be done per component, because component client reuse the same logic for all
    val commandHandlerMethods = if (classOf[EventSourcedEntity[_, _]].isAssignableFrom(component)) {
      component.getDeclaredMethods.collect {
        case method if isCommandHandlerCandidate[EventSourcedEntity.Effect[_]](method) =>
          method.getName.capitalize -> MethodInvoker(method)
      }
    } else if (classOf[KeyValueEntity[_]].isAssignableFrom(component)) {
      component.getDeclaredMethods.collect {
        case method if isCommandHandlerCandidate[KeyValueEntity.Effect[_]](method) =>
          method.getName.capitalize -> MethodInvoker(method)
      }
    } else if (classOf[Workflow[_]].isAssignableFrom(component)) {
      component.getDeclaredMethods.collect {
        case method if isCommandHandlerCandidate[Workflow.Effect[_]](method) =>
          method.getName.capitalize -> MethodInvoker(method)
      }
    } else {

      // should never happen
      throw new RuntimeException(
        s"Unsupported component type: ${component.getName}. Supported types are: EventSourcedEntity, ValueEntity, Workflow")
    }

    ComponentDescriptor(commandHandlerMethods.toMap)
  }
}
