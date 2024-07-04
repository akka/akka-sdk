/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.impl

import com.google.protobuf.Descriptors
import com.google.protobuf.DynamicMessage
import com.google.protobuf.any.{ Any => ScalaPbAny }
import akka.platform.javasdk.impl.AnySupport.BytesPrimitive
import akka.platform.javasdk.impl.reflection.DynamicMessageContext
import akka.platform.javasdk.impl.reflection.MetadataContext
import akka.platform.javasdk.JsonSupport
import akka.platform.javasdk.Metadata

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
