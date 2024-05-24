/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package kalix.javasdk.impl

import java.lang.reflect.Method

import scala.reflect.ClassTag

import kalix.KeyGeneratorMethodOptions.Generator
import kalix.javasdk.eventsourcedentity.EventSourcedEntity
import kalix.javasdk.impl.JwtDescriptorFactory.buildJWTOptions
import kalix.javasdk.impl.ComponentDescriptorFactory.mergeServiceOptions
import kalix.javasdk.impl.reflection.CommandHandlerMethod
import kalix.javasdk.impl.reflection.EntityUrlTemplate
import kalix.javasdk.impl.reflection.IdExtractor.extractIds
import kalix.javasdk.impl.reflection.KalixMethod
import kalix.javasdk.impl.reflection.NameGenerator
import kalix.javasdk.impl.reflection.RestServiceIntrospector

private[impl] object EntityDescriptorFactory extends ComponentDescriptorFactory {

  override def buildDescriptorFor(
      component: Class[_],
      messageCodec: JsonMessageCodec,
      nameGenerator: NameGenerator): ComponentDescriptor = {

    // command handlers candidate must have 0 or 1 parameter and return the components effect type
    // we might later revisit this, instead of single param, we can require (State, Cmd) => Effect like in Akka
    def isCommandHandlerCandidate[E](method: Method)(implicit effectType: ClassTag[E]): Boolean = {
      effectType.runtimeClass.isAssignableFrom(method.getReturnType) && method.getParameterTypes.length <= 1
    }

    val kalixMethods =
      if (classOf[EventSourcedEntity[_, _]].isAssignableFrom(component)) {
        component.getDeclaredMethods.collect {
          case method if isCommandHandlerCandidate[EventSourcedEntity.Effect[_]](method) =>
            val servMethod = CommandHandlerMethod(component, method, EntityUrlTemplate)
            KalixMethod(servMethod, entityIds = Seq("entity-id"))
              .withKalixOptions(buildJWTOptions(method))
        }.toSeq
      } else {
        RestServiceIntrospector.inspectService(component).methods.map { restMethod =>
          val ids = extractIds(component, restMethod.javaMethod)
          val kalixMethod =
            if (ids.isEmpty) {
              val idGenOptions = kalix.IdGeneratorMethodOptions.newBuilder().setAlgorithm(Generator.VERSION_4_UUID)
              val methodOpts = kalix.MethodOptions.newBuilder().setIdGenerator(idGenOptions)
              KalixMethod(restMethod).withKalixOptions(methodOpts.build())
            } else {
              KalixMethod(restMethod, entityIds = ids)
            }
          kalixMethod.withKalixOptions(buildJWTOptions(restMethod.javaMethod))
        }
      }

    val serviceName = nameGenerator.getName(component.getSimpleName)
    ComponentDescriptor(
      nameGenerator,
      messageCodec,
      serviceName,
      serviceOptions = mergeServiceOptions(
        AclDescriptorFactory.serviceLevelAclAnnotation(component),
        JwtDescriptorFactory.serviceLevelJwtAnnotation(component)),
      component.getPackageName,
      kalixMethods)
  }
}
