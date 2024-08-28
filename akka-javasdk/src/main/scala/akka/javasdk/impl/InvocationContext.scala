/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.javasdk.impl.reflection.DynamicMessageContext
import akka.javasdk.impl.reflection.MetadataContext
import com.google.protobuf.Descriptors
import com.google.protobuf.DynamicMessage
import com.google.protobuf.any.{ Any => ScalaPbAny }
import AnySupport.BytesPrimitive
import akka.javasdk.JsonSupport
import akka.javasdk.Metadata

object InvocationContext {

  private val typeUrlField = ScalaPbAny.javaDescriptor.findFieldByName("type_url")
  private val valueField = ScalaPbAny.javaDescriptor.findFieldByName("value")

  def apply(
      anyMessage: ScalaPbAny,
      methodDescriptor: Descriptors.Descriptor,
      metadata: Metadata = Metadata.EMPTY): InvocationContext = {

    val dynamicMessage =
      if (anyMessage.typeUrl.startsWith(JsonSupport.KALIX_JSON) ||
        anyMessage.typeUrl == BytesPrimitive.fullName) {
        // FIXME how can this ever work unless methodDescriptor is protobuf Any, or a synthetic
        //       message with exactly the two fields type_url and value?
        DynamicMessage
          .newBuilder(methodDescriptor)
          .setField(typeUrlField, anyMessage.typeUrl)
          .setField(valueField, anyMessage.value)
          .build()

      } else {
        DynamicMessage.parseFrom(methodDescriptor, anyMessage.value)
      }

    new InvocationContext(dynamicMessage, metadata)
  }
}
class InvocationContext(val message: DynamicMessage, val metadata: Metadata)
    extends DynamicMessageContext
    with MetadataContext
