/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.reflection

import akka.annotation.InternalApi
import akka.javasdk.Metadata
import akka.javasdk.impl.AnySupport
import scala.jdk.OptionConverters._

import com.google.protobuf.ByteString
import com.google.protobuf.Descriptors
import com.google.protobuf.DynamicMessage
import com.google.protobuf.{ Any => JavaPbAny }
import com.google.protobuf.any.{ Any => ScalaPbAny }
import akka.javasdk.impl.ErrorHandling.BadRequestException
import akka.javasdk.impl.serialization.JsonSerializer
import akka.runtime.sdk.spi.BytesPayload

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

  private def decodeParam[T](pbAny: ScalaPbAny, cls: Class[T], serializer: JsonSerializer): T = {
    if (cls == classOf[Array[Byte]]) {
      val bytes = pbAny.value
      AnySupport.decodePrimitiveBytes(bytes).toByteArray.asInstanceOf[T]
    } else {
      // FIXME we should not need these conversions
      val bytesPayload = AnySupport.toSpiBytesPayload(pbAny)
      serializer.fromBytes(cls, bytesPayload)
    }
  }

  private def decodeParam[T](payload: BytesPayload, cls: Class[T], serializer: JsonSerializer): T = {
    if (cls == classOf[Array[Byte]]) {
      payload.bytes.toArrayUnsafe().asInstanceOf[T]
    } else {
      serializer.fromBytes(cls, payload)
    }
  }

  private def decodeParamPossiblySealed[T](pbAny: ScalaPbAny, cls: Class[T], serializer: JsonSerializer): T = {
    if (cls.isSealed) {
      // FIXME we should not need these conversions
      val bytesPayload = AnySupport.toSpiBytesPayload(pbAny)
      serializer.fromBytes(bytesPayload).asInstanceOf[T]
    } else {
      decodeParam(pbAny, cls, serializer)
    }
  }

  def decodeParamPossiblySealed[T](payload: BytesPayload, cls: Class[T], serializer: JsonSerializer): T = {
    if (cls.isSealed) {
      serializer.fromBytes(payload).asInstanceOf[T]
    } else {
      decodeParam(payload, cls, serializer)
    }
  }

  private def decodeParamCollection[T, C <: java.util.Collection[T]](
      dm: DynamicMessage,
      cls: Class[T],
      collectionType: Class[C],
      serializer: JsonSerializer): C = {
    // FIXME we should not need these conversions
    val pbAny = ScalaPbAny.fromJavaProto(toAny(dm))
    val bytesPayload = AnySupport.toSpiBytesPayload(pbAny)
    serializer.fromBytes(cls, collectionType, bytesPayload)
  }

  case class AnyBodyExtractor[T](cls: Class[_], serializer: JsonSerializer)
      extends ParameterExtractor[DynamicMessageContext, T] {
    override def extract(context: DynamicMessageContext): T =
      decodeParamPossiblySealed(context.getAny, cls.asInstanceOf[Class[T]], serializer)
  }

  class BodyExtractor[T](field: Descriptors.FieldDescriptor, cls: Class[_], serializer: JsonSerializer)
      extends ParameterExtractor[DynamicMessageContext, T] {

    override def extract(context: DynamicMessageContext): T = {
      context.getField(field) match {
        case dm: DynamicMessage =>
          decodeParam(ScalaPbAny.fromJavaProto(toAny(dm)), cls.asInstanceOf[Class[T]], serializer)
      }
    }
  }

  class CollectionBodyExtractor[T, C <: java.util.Collection[T]](
      field: Descriptors.FieldDescriptor,
      cls: Class[T],
      collectionType: Class[C],
      serializer: JsonSerializer)
      extends ParameterExtractor[DynamicMessageContext, C] {

    override def extract(context: DynamicMessageContext): C = {
      context.getField(field) match {
        case dm: DynamicMessage => decodeParamCollection(dm, cls, collectionType, serializer)
      }
    }
  }

  class FieldExtractor[T](field: Descriptors.FieldDescriptor, required: Boolean, deserialize: AnyRef => T)
      extends ParameterExtractor[DynamicMessageContext, T] {
    override def extract(context: DynamicMessageContext): T = {
      (required, field.isRepeated || context.hasField(field)) match {
        case (_, true) => deserialize(context.getField(field))
        //we know that currently this applies only to request parameters
        case (true, false)  => throw BadRequestException(s"Required request parameter is missing: ${field.getName}")
        case (false, false) => null.asInstanceOf[T] //could be mapped to optional later on
      }
    }
  }

  class HeaderExtractor[T >: Null](name: String, deserialize: String => T)
      extends ParameterExtractor[MetadataContext, T] {
    override def extract(context: MetadataContext): T = context.metadata.get(name).toScala.map(deserialize).orNull
  }
}
