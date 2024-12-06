/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.annotation.InternalApi
import akka.javasdk.impl.serialization.JsonSerializer
import com.google.protobuf.Descriptors

/**
 * Service describes a component type in a way which makes it possible to deploy.
 *
 * @param componentType
 *   The runtime name of the type of component (action, es entity etc.)
 * @param additionalDescriptors
 *   a Protobuf FileDescriptor of any APIs that need to be available either to API consumers (message types etc) or the
 *   backoffice API (state model etc.).
 *
 * INTERNAL API
 */
@InternalApi
private[akka] abstract class Service(
    componentClass: Class[_],
    val componentType: String,
    val serializer: JsonSerializer) {
  val componentId: String = ComponentDescriptorFactory.readComponentIdIdValue(componentClass)
  val componentDescriptor = ComponentDescriptor.descriptorFor(componentClass, serializer)
  val descriptor: Descriptors.ServiceDescriptor = componentDescriptor.serviceDescriptor
  val additionalDescriptors: Array[Descriptors.FileDescriptor] = Array(componentDescriptor.fileDescriptor)
}
