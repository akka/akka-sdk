/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.annotation.InternalApi
import akka.javasdk.impl.AnySupport.ProtobufEmptyTypeUrl
import akka.javasdk.impl.ComponentDescriptorFactory._
import akka.javasdk.impl.reflection.Reflect
import akka.javasdk.impl.serialization.JsonSerializer

/**
 * INTERNAL API
 */
@InternalApi
private[impl] object ConsumerDescriptorFactory extends ComponentDescriptorFactory {

  override def buildDescriptorFor(component: Class[_], serializer: JsonSerializer): ComponentDescriptor = {

    import Reflect.methodOrdering

    val handleDeletesMethods: Map[String, MethodInvoker] = component.getMethods
      .filter(hasConsumerOutput)
      .filter(hasHandleDeletes)
      .sorted
      .map { method =>
        ProtobufEmptyTypeUrl -> MethodInvoker(method)
      }
      .toMap

    val methods: Map[String, MethodInvoker] = component.getMethods
      .filter(hasConsumerOutput)
      .filterNot(hasHandleDeletes)
      .flatMap { method =>
        method.getParameterTypes.headOption match {
          case Some(inputType) =>
            val invoker = MethodInvoker(method)
            if (method.getParameterTypes.last.isSealed) {
              method.getParameterTypes.last.getPermittedSubclasses.toList
                .flatMap(subClass => {
                  serializer.contentTypesFor(subClass).map(typeUrl => typeUrl -> invoker)
                })
            } else {
              val typeUrls = serializer.contentTypesFor(inputType)
              typeUrls.map(_ -> invoker)
            }
          case None =>
            // FIXME check if there is a validation for that already
            throw new IllegalStateException(
              "Consumer method must have at least one parameter, unless it is a delete handler")
        }
      }
      .toMap

    val allInvokers = methods ++ handleDeletesMethods

    //Empty command/method name, because it is not used in the consumer, we just need the invokers
    ComponentDescriptor(allInvokers)
  }
}
