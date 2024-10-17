/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.javasdk.impl.reflection.CommandHandlerMethod
import akka.javasdk.impl.reflection.EntityUrlTemplate
import akka.javasdk.impl.reflection.KalixMethod
import akka.javasdk.impl.reflection.NameGenerator
import akka.javasdk.impl.reflection.WorkflowUrlTemplate

import java.lang.reflect.Method
import scala.reflect.ClassTag
import ComponentDescriptorFactory.mergeServiceOptions
import JwtDescriptorFactory.buildJWTOptions
import akka.annotation.InternalApi
import akka.javasdk.eventsourcedentity.EventSourcedEntity
import akka.javasdk.keyvalueentity.KeyValueEntity
import akka.javasdk.workflow.Workflow

/**
 * INTERNAL API
 */
@InternalApi
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
            val readOnlyCommandHandler = method.getReturnType == classOf[EventSourcedEntity.ReadOnlyEffect[_]]
            var options = buildJWTOptions(method)
            if (readOnlyCommandHandler)
              options = Some(
                options
                  .map(_.toBuilder)
                  .getOrElse(kalix.MethodOptions.newBuilder())
                  .setReadOnly(true)
                  .build())
            KalixMethod(servMethod, entityIds = Seq("entity-id"))
              .withKalixOptions(options)
        }.toSeq

      } else if (classOf[KeyValueEntity[_]].isAssignableFrom(component)) {
        component.getDeclaredMethods.collect {
          case method if isCommandHandlerCandidate[KeyValueEntity.Effect[_]](method) =>
            val servMethod = CommandHandlerMethod(component, method, EntityUrlTemplate)
            KalixMethod(servMethod, entityIds = Seq("entity-id"))
              .withKalixOptions(buildJWTOptions(method))
        }.toSeq
      } else if (classOf[Workflow[_]].isAssignableFrom(component)) {
        component.getDeclaredMethods.collect {
          case method if isCommandHandlerCandidate[Workflow.Effect[_]](method) =>
            val servMethod = CommandHandlerMethod(component, method, WorkflowUrlTemplate)
            val readOnlyCommandHandler = method.getReturnType == classOf[Workflow.ReadOnlyEffect[_]]
            var options = buildJWTOptions(method)
            if (readOnlyCommandHandler)
              options = Some(
                options
                  .map(_.toBuilder)
                  .getOrElse(kalix.MethodOptions.newBuilder())
                  .setReadOnly(true)
                  .build())
            KalixMethod(servMethod, entityIds = Seq("entity-id"))
              .withKalixOptions(options)
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
