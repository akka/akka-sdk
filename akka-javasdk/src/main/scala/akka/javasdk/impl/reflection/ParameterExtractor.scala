/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.reflection

import akka.annotation.InternalApi
import akka.javasdk.impl.serialization.Serializer
import akka.runtime.sdk.spi.BytesPayload
import com.google.protobuf.GeneratedMessageV3

/**
 * INTERNAL API
 */
@InternalApi
private[impl] object ParameterExtractors {

  private def decodeParam[T](payload: BytesPayload, cls: Class[T], serializer: Serializer): T = {
    if (cls == classOf[Array[Byte]]) {
      payload.bytes.toArrayUnsafe().asInstanceOf[T]
    } else {
      // Serializer now handles both JSON and protobuf detection internally
      serializer.fromBytes(cls, payload)
    }
  }

  def decodeParamPossiblySealed[T](payload: BytesPayload, cls: Class[T], serializer: Serializer): T = {
    if (cls.isSealed || cls == classOf[GeneratedMessageV3]) {
      // For sealed classes and base GeneratedMessageV3, deserialize using the content type
      // to resolve the concrete type rather than trying to use the base class directly
      serializer.fromBytes(payload).asInstanceOf[T]
    } else {
      decodeParam(payload, cls, serializer)
    }
  }

}
