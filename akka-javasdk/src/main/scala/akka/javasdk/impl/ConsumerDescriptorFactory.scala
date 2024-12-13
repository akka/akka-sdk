/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.annotation.InternalApi
import akka.javasdk.impl.ComponentDescriptorFactory._
import akka.javasdk.impl.reflection.HandleDeletesServiceMethod
import akka.javasdk.impl.reflection.KalixMethod
import akka.javasdk.impl.reflection.Reflect
import akka.javasdk.impl.reflection.SubscriptionServiceMethod
import akka.javasdk.impl.serialization.JsonSerializer

/**
 * INTERNAL API
 */
@InternalApi
private[impl] object ConsumerDescriptorFactory extends ComponentDescriptorFactory {

  override def buildDescriptorFor(component: Class[_], serializer: JsonSerializer): ComponentDescriptor = {

    import Reflect.methodOrdering

    val handleDeletesMethods = component.getMethods
      .filter(hasConsumerOutput)
      .filter(hasHandleDeletes)
      .sorted
      .map { method =>
        KalixMethod(HandleDeletesServiceMethod(method))
      }
      .toSeq

    val methods = component.getMethods
      .filter(hasConsumerOutput)
      .filterNot(hasHandleDeletes)
      .map { method =>
        KalixMethod(SubscriptionServiceMethod(method))
      }
      .toIndexedSeq

    val allMethods = methods ++ handleDeletesMethods

    val commandHandlers = allMethods.map { method =>
      method.toCommandHandler(serializer)
    }

    //folding all invokers into a single map
    val allInvokers = commandHandlers.foldLeft(Map.empty[String, MethodInvoker]) { (acc, handler) =>
      acc ++ handler.methodInvokers
    }

    //Empty command/method name, because it is not used in the consumer, we just need the invokers
    ComponentDescriptor(Map("" -> CommandHandler(null, serializer, null, allInvokers)))
  }
}
