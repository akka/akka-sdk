/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package kalix.javasdk.impl

import java.lang.reflect.Method
import scala.reflect.ClassTag
import kalix.javasdk.eventsourcedentity.EventSourcedEntity
import kalix.javasdk.impl.ComponentDescriptorFactory.mergeServiceOptions
import kalix.javasdk.impl.JwtDescriptorFactory.buildJWTOptions
import kalix.javasdk.impl.reflection.CommandHandlerMethod
import kalix.javasdk.impl.reflection.EntityUrlTemplate
import kalix.javasdk.impl.reflection.KalixMethod
import kalix.javasdk.impl.reflection.NameGenerator
import kalix.javasdk.impl.reflection.WorkflowUrlTemplate
import kalix.javasdk.valueentity.ValueEntity
import kalix.javasdk.workflow.AbstractWorkflow

private[impl] object EntityDescriptorFactory extends ComponentDescriptorFactory {

  override def buildDescriptorFor(
      component: Class[_],
      messageCodec: JsonMessageCodec,
      nameGenerator: NameGenerator): ComponentDescriptor = {

    // command handlers candidate must have 0 or 1 parameter and return the components effect type
    // we might later revisit this, instead of single param, we can require (State, Cmd) => Effect like in Akka
    def isCommandHandlerCandidate[E](method: Method)(implicit effectType: ClassTag[E]): Boolean = {
      effectType.runtimeClass.isAssignableFrom(method.getReturnType) &&
      method.getParameterTypes.length <= 1 &&
      // Workflow will have lambdas returning Effect, we want to filter them out
      !method.getName.startsWith("lambda$")
    }

    val kalixMethods =
      if (classOf[EventSourcedEntity[_, _]].isAssignableFrom(component)) {
        component.getDeclaredMethods.collect {
          case method if isCommandHandlerCandidate[EventSourcedEntity.Effect[_]](method) =>
            val servMethod = CommandHandlerMethod(component, method, EntityUrlTemplate)
            KalixMethod(servMethod, entityIds = Seq("entity-id"))
              .withKalixOptions(buildJWTOptions(method))
        }.toSeq

      } else if (classOf[ValueEntity[_]].isAssignableFrom(component)) {
        component.getDeclaredMethods.collect {
          case method if isCommandHandlerCandidate[ValueEntity.Effect[_]](method) =>
            val servMethod = CommandHandlerMethod(component, method, EntityUrlTemplate)
            KalixMethod(servMethod, entityIds = Seq("entity-id"))
              .withKalixOptions(buildJWTOptions(method))
        }.toSeq
      } else if (classOf[AbstractWorkflow[_]].isAssignableFrom(component)) {
        component.getDeclaredMethods.collect {
          case method if isCommandHandlerCandidate[AbstractWorkflow.Effect[_]](method) =>
            val servMethod = CommandHandlerMethod(component, method, WorkflowUrlTemplate)
            KalixMethod(servMethod, entityIds = Seq("entity-id"))
              .withKalixOptions(buildJWTOptions(method))
        }.toSeq
      } else {
        // should never happen
        throw new RuntimeException(
          s"Unsupported component type: ${component.getName}. Supported types are: EventSourcedEntity, ValueEntity, Workflow")
      }

    val serviceName = nameGenerator.getName(component.getSimpleName)
    ComponentDescriptor(
      nameGenerator,
      messageCodec,
      serviceName,
      serviceOptions = mergeServiceOptions(
        AclDescriptorFactory.serviceLevelAclAnnotation(component, default = Some(AclDescriptorFactory.denyAll)),
        JwtDescriptorFactory.serviceLevelJwtAnnotation(component)),
      component.getPackageName,
      kalixMethods)
  }
}
