/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.annotation.InternalApi
import akka.javasdk.impl.AnySupport.ProtobufEmptyTypeUrl
import akka.javasdk.impl.ComponentDescriptorFactory._
import akka.javasdk.impl.ErrorHandling.unwrapInvocationTargetExceptionCatcher
import akka.javasdk.impl.reflection.Reflect
import akka.javasdk.impl.serialization.JsonSerializer
import com.google.protobuf.GeneratedMessageV3

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
            if (inputType.isSealed) {
              inputType.getPermittedSubclasses.toList
                .flatMap(subClass => {
                  serializer.contentTypesFor(subClass).map(typeUrl => typeUrl -> invoker)
                })
            } else if (classOf[GeneratedMessageV3].isAssignableFrom(inputType)) {
              // special handling of protobuf message types
              val descriptor =
                try {
                  inputType
                    .getMethod("getDescriptor")
                    .invoke(null)
                    .asInstanceOf[com.google.protobuf.Descriptors.Descriptor]
                } catch unwrapInvocationTargetExceptionCatcher

              Seq(AnySupport.DefaultTypeUrlPrefix + "/" + descriptor.getFullName -> invoker)
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
