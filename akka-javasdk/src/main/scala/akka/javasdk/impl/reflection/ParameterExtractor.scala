/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.reflection

import akka.annotation.InternalApi
import akka.javasdk.Metadata
import akka.javasdk.impl.serialization.JsonSerializer
import akka.runtime.sdk.spi.BytesPayload
import com.google.protobuf.ByteString
import com.google.protobuf.Descriptors
import com.google.protobuf.DynamicMessage
import com.google.protobuf.any.{ Any => ScalaPbAny }
import com.google.protobuf.{ Any => JavaPbAny }

/**
 * Extracts method parameters from an invocation context for the purpose of passing them to a reflective invocation call
 *
 * INTERNAL API
 */
@InternalApi
private[impl] trait ParameterExtractor[-C, +T] {
  def extract(context: C): T
}

/**
 * INTERNAL API
 */
@InternalApi
private[impl] trait MetadataContext {
  def metadata: Metadata
}

/**
 * INTERNAL API
 */
@InternalApi
private[impl] trait DynamicMessageContext {
  def getAny: ScalaPbAny
  def getField(field: Descriptors.FieldDescriptor): AnyRef
  def hasField(field: Descriptors.FieldDescriptor): Boolean
}

/**
 * INTERNAL API
 */
@InternalApi
private[impl] object ParameterExtractors {

  def toAny(dm: DynamicMessage) = {
    val bytes = dm.getField(JavaPbAny.getDescriptor.findFieldByName("value")).asInstanceOf[ByteString]
    val typeUrl = dm.getField(JavaPbAny.getDescriptor.findFieldByName("type_url")).asInstanceOf[String]
    // TODO: avoid creating a new JavaPbAny instance
    // we want to reuse the typeUrl validation and reading logic (skip tag + jackson reader) from JsonSupport
    // we need a new internal version that also handle DynamicMessages
    JavaPbAny
      .newBuilder()
      .setTypeUrl(typeUrl)
      .setValue(bytes)
      .build()
  }

  private def decodeParam[T](payload: BytesPayload, cls: Class[T], serializer: JsonSerializer): T = {
    if (cls == classOf[Array[Byte]]) {
      payload.bytes.toArrayUnsafe().asInstanceOf[T]
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
