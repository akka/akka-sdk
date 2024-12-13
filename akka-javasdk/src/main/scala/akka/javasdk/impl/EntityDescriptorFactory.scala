/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import java.lang.reflect.Method

import scala.reflect.ClassTag

import akka.annotation.InternalApi
import akka.javasdk.eventsourcedentity.EventSourcedEntity
import akka.javasdk.impl.reflection.ActionHandlerMethod
import akka.javasdk.impl.reflection.KalixMethod
import akka.javasdk.impl.serialization.JsonSerializer
import akka.javasdk.keyvalueentity.KeyValueEntity
import akka.javasdk.workflow.Workflow

/**
 * INTERNAL API
 */
@InternalApi
private[impl] object EntityDescriptorFactory extends ComponentDescriptorFactory {

  override def buildDescriptorFor(component: Class[_], serializer: JsonSerializer): ComponentDescriptor = {

    // command handlers candidate must have 0 or 1 parameter and return the components effect type
    // we might later revisit this, instead of single param, we can require (State, Cmd) => Effect like in Akka
    def isCommandHandlerCandidate[E](method: Method)(implicit effectType: ClassTag[E]): Boolean = {
      effectType.runtimeClass.isAssignableFrom(method.getReturnType) &&
      method.getParameterTypes.length <= 1 &&
      // Workflow will have lambdas returning Effect, we want to filter them out
      !method.getName.startsWith("lambda$")
    }

    val commandHandlerMethods: Seq[KalixMethod] = if (classOf[EventSourcedEntity[_, _]].isAssignableFrom(component)) {
      component.getDeclaredMethods.collect {
        case method if isCommandHandlerCandidate[EventSourcedEntity.Effect[_]](method) =>
          val servMethod = ActionHandlerMethod(component, method)
          KalixMethod(servMethod, entityIds = Seq.empty)
      }.toSeq
    } else if (classOf[KeyValueEntity[_]].isAssignableFrom(component)) {
      component.getDeclaredMethods.collect {
        case method if isCommandHandlerCandidate[KeyValueEntity.Effect[_]](method) =>
          val servMethod = ActionHandlerMethod(component, method)
          KalixMethod(servMethod, entityIds = Seq.empty)
      }.toSeq
    } else if (classOf[Workflow[_]].isAssignableFrom(component)) {
      component.getDeclaredMethods.collect {
        case method if isCommandHandlerCandidate[Workflow.Effect[_]](method) =>
          val servMethod = ActionHandlerMethod(component, method)
          KalixMethod(servMethod, entityIds = Seq.empty)
      }.toSeq
    } else {

      // should never happen
      throw new RuntimeException(
        s"Unsupported component type: ${component.getName}. Supported types are: EventSourcedEntity, ValueEntity, Workflow")
    }

    ComponentDescriptor(serializer, commandHandlerMethods)
  }
}
