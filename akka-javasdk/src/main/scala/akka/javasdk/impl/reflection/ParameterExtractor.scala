/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.reflection

import akka.annotation.InternalApi
import akka.javasdk.impl.AnySupport
import akka.javasdk.impl.serialization.JsonSerializer
import akka.runtime.sdk.spi.BytesPayload
import com.google.protobuf.GeneratedMessageV3

/**
 * INTERNAL API
 */
@InternalApi
private[impl] object ParameterExtractors {

  private def decodeParam[T](payload: BytesPayload, cls: Class[T], serializer: JsonSerializer): T = {
    if (cls == classOf[Array[Byte]]) {
      payload.bytes.toArrayUnsafe().asInstanceOf[T]
    } else if (classOf[GeneratedMessageV3].isAssignableFrom(cls)) {
      AnySupport.decodeJavaProtobuf(payload, cls)
    } else {
      serializer.fromBytes(cls, payload)
    }
  }

  def decodeParamPossiblySealed[T](payload: BytesPayload, cls: Class[T], serializer: JsonSerializer): T = {
    if (cls.isSealed) {
      serializer.fromBytes(payload).asInstanceOf[T]
    } else {
      decodeParam(payload, cls, serializer)
    }
  }

}
