/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.annotation.InternalApi
import akka.javasdk.impl.AnySupport.ProtobufEmptyTypeUrl
import akka.javasdk.impl.ComponentDescriptorFactory._
import akka.javasdk.impl.ErrorHandling.unwrapInvocationTargetExceptionCatcher
import akka.javasdk.impl.reflection.Reflect
import akka.javasdk.impl.serialization.Serializer
import com.google.protobuf.GeneratedMessageV3

/**
 * INTERNAL API
 */
@InternalApi
private[impl] object ConsumerDescriptorFactory extends ComponentDescriptorFactory {

  override def buildDescriptorFor(component: Class[_], serializer: Serializer): ComponentDescriptor = {

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
            try {
              val invoker = MethodInvoker(method)
              if (inputType.isSealed) {
                inputType.getPermittedSubclasses.toList
                  .flatMap(subClass => {
                    serializer.contentTypesFor(subClass).map(typeUrl => typeUrl -> invoker)
                  })
              } else if (classOf[GeneratedMessageV3].isAssignableFrom(inputType)) {
                // special handling of protobuf message types, catch all
                if (inputType == classOf[GeneratedMessageV3]) {
                  // Base GeneratedMessageV3 handler - resolve concrete proto types
                  val protoTypes = Reflect.resolveProtoEventTypes(component)
                  if (protoTypes.isEmpty) {
                    throw new IllegalStateException(
                      s"Consumer [${component.getName}] handler method [${method.getName}] accepts GeneratedMessageV3 " +
                      "but no concrete proto event types could be resolved. Add @ProtoEventTypes to the consumer class " +
                      "or to the source event sourced entity.")
                  }
                  protoTypes.flatMap { protoClass =>
                    serializer.registerTypeHints(protoClass)
                    serializer.contentTypesFor(protoClass).map(_ -> invoker)
                  }
                } else {
                  // specific concrete proto message input types
                  val descriptor = Reflect.protoDescriptorFor(inputType.asSubclass(classOf[GeneratedMessageV3]))
                  Seq(AnySupport.DefaultTypeUrlPrefix + "/" + descriptor.getFullName -> invoker)
                }
              } else {
                val typeUrls = serializer.contentTypesFor(inputType)
                typeUrls.map(_ -> invoker)
              }
            } catch unwrapInvocationTargetExceptionCatcher
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
