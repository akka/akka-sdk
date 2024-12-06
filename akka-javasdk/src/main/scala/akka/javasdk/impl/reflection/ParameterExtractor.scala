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
  def message: DynamicMessage
}

/**
 * INTERNAL API
 */
@InternalApi
private[impl] object ParameterExtractors {

  private def toAny(dm: DynamicMessage) = {
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

  private def decodeParam[T](dm: DynamicMessage, cls: Class[T], serializer: JsonSerializer): T = {
    if (cls == classOf[Array[Byte]]) {
      val bytes = dm.getField(JavaPbAny.getDescriptor.findFieldByName("value")).asInstanceOf[ByteString]
      AnySupport.decodePrimitiveBytes(bytes).toByteArray.asInstanceOf[T]
    } else {
      // FIXME we should not need these conversions
      val pbAny = ScalaPbAny.fromJavaProto(toAny(dm))
      val bytesPayload = AnySupport.toSpiBytesPayload(pbAny)
      serializer.fromBytes(cls, bytesPayload)
    }
  }

  private def decodeParamPossiblySealed[T](dm: DynamicMessage, cls: Class[T], serializer: JsonSerializer): T = {
    if (cls.isSealed) {
      // FIXME we should not need these conversions
      val pbAny = ScalaPbAny.fromJavaProto(toAny(dm))
      val bytesPayload = AnySupport.toSpiBytesPayload(pbAny)
      serializer.fromBytes(bytesPayload).asInstanceOf[T]
    } else {
      decodeParam(dm, cls, serializer)
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
      decodeParamPossiblySealed(context.message, cls.asInstanceOf[Class[T]], serializer)
  }

  class BodyExtractor[T](field: Descriptors.FieldDescriptor, cls: Class[_], serializer: JsonSerializer)
      extends ParameterExtractor[DynamicMessageContext, T] {

    override def extract(context: DynamicMessageContext): T = {
      context.message.getField(field) match {
        case dm: DynamicMessage => decodeParam(dm, cls.asInstanceOf[Class[T]], serializer)
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
      context.message.getField(field) match {
        case dm: DynamicMessage => decodeParamCollection(dm, cls, collectionType, serializer)
      }
    }
  }

  class FieldExtractor[T](field: Descriptors.FieldDescriptor, required: Boolean, deserialize: AnyRef => T)
      extends ParameterExtractor[DynamicMessageContext, T] {
    override def extract(context: DynamicMessageContext): T = {
      (required, field.isRepeated || context.message.hasField(field)) match {
        case (_, true) => deserialize(context.message.getField(field))
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
