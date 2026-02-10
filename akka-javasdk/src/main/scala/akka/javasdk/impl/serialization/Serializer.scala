/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.serialization

import java.lang.reflect.Type

import akka.annotation.InternalApi
import akka.javasdk.impl.AnySupport.BytesPrimitive
import akka.runtime.sdk.spi.BytesPayload
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.protobuf.GeneratedMessageV3

/**
 * INTERNAL API
 *
 * Composite serializer that automatically detects and delegates to either JsonSerializer or ProtobufSerializer based on
 * the type being serialized/deserialized.
 */
@InternalApi
object Serializer {
  val JsonContentTypePrefix: String = JsonSerializer.JsonContentTypePrefix
  val ProtobufContentTypePrefix: String = ProtobufSerializer.ProtobufContentTypePrefix

  def newObjectMapperWithDefaults(): ObjectMapper = JsonSerializer.newObjectMapperWithDefaults()
}

/**
 * INTERNAL API
 */
@InternalApi
final class Serializer(val objectMapper: ObjectMapper) {

  def this() = this(JsonSerializer.internalObjectMapper)

  private val jsonSerializer = new JsonSerializer(objectMapper)
  private lazy val protobufSerializer = new ProtobufSerializer()

  // Expose the underlying serializers for cases where specific behavior is needed
  def json: JsonSerializer = jsonSerializer
  def proto: ProtobufSerializer = protobufSerializer

  /**
   * Serialize a value to bytes. Automatically detects protobuf messages and serializes them to binary protobuf format,
   * otherwise uses JSON.
   */
  def toBytes(value: Any): BytesPayload = {
    value match {
      case null                      => throw new NullPointerException("Null value handed to serialization")
      case proto: GeneratedMessageV3 => ProtobufSerializer.toBytes(proto)
      case _                         => jsonSerializer.toBytes(value)
    }
  }

  /**
   * Serialize a value to JSON bytes, even if it's a protobuf message.
   */
  def toBytesAsJson(value: Any): BytesPayload = {
    value match {
      case null                  => throw new NullPointerException("Null value handed to serialization")
      case _: GeneratedMessageV3 => ProtobufSerializer.toBytesAsJson(value)
      case _                     => jsonSerializer.toBytes(value) // Already JSON
    }
  }

  /**
   * Deserialize bytes to the expected type. Automatically detects the content type and uses the appropriate
   * deserializer.
   */
  def fromBytes[T](expectedType: Class[T], bytesPayload: BytesPayload): T = {
    if (classOf[GeneratedMessageV3].isAssignableFrom(expectedType)) {
      if (isProtobuf(bytesPayload))
        ProtobufSerializer
          .fromBytes(expectedType.asInstanceOf[Class[GeneratedMessageV3]], bytesPayload)
          .asInstanceOf[T]
      else if (isJson(bytesPayload))
        // View results come back as JSON even for protobuf types (views store data as JSONB)
        ProtobufSerializer
          .fromBytesAsJson(expectedType.asInstanceOf[Class[GeneratedMessageV3]], bytesPayload)
          .asInstanceOf[T]
      else
        throw new IllegalArgumentException(
          s"Expected protobuf message matching generated class [${expectedType}] but payload has type [${bytesPayload.contentType}]")
    } else if (isJson(bytesPayload)) {
      jsonSerializer.fromBytes(expectedType, bytesPayload)
    } else {
      throw new IllegalArgumentException(s"Unknown content type [${bytesPayload.contentType}]")
    }
  }

  /**
   * Deserialize bytes to the expected type using Type parameter (for generic types).
   */
  def fromBytes[T](expectedType: Type, bytesPayload: BytesPayload): T =
    expectedType match {
      case cls: Class[_] if classOf[GeneratedMessageV3].isAssignableFrom(cls) =>
        if (isProtobuf(bytesPayload))
          ProtobufSerializer
            .fromBytes(cls.asInstanceOf[Class[_ <: GeneratedMessageV3]], bytesPayload)
            .asInstanceOf[T]
        else if (isJson(bytesPayload))
          ProtobufSerializer
            .fromBytesAsJson(cls.asInstanceOf[Class[_ <: GeneratedMessageV3]], bytesPayload)
            .asInstanceOf[T]
        else
          throw new IllegalArgumentException(
            s"Expected protobuf message matching generated class [${cls}] but payload has type [${bytesPayload.contentType}]")
      case _ => jsonSerializer.fromBytes(expectedType, bytesPayload)
    }

  /**
   * Deserialize bytes to an object based on the content type. Requires that types are registered via registerTypeHints.
   */
  def fromBytes(bytesPayload: BytesPayload): AnyRef = {
    if (isProtobuf(bytesPayload)) {
      protobufSerializer.fromBytes(bytesPayload)
    } else {
      jsonSerializer.fromBytes(bytesPayload)
    }
  }

  def isJson(bytesPayload: BytesPayload): Boolean =
    jsonSerializer.isJson(bytesPayload)

  def isJsonContentType(contentType: String): Boolean =
    jsonSerializer.isJsonContentType(contentType)

  def isProtobuf(bytesPayload: BytesPayload): Boolean =
    ProtobufSerializer.isProtobuf(bytesPayload)

  /**
   * Get the content type for a class. Returns protobuf content type for protobuf classes, JSON content type otherwise.
   */
  def contentTypeFor(clz: Class[_]): String = {
    if (ProtobufSerializer.isProtobufClass(clz)) {
      ProtobufSerializer.contentTypeFor(clz.asInstanceOf[Class[_ <: GeneratedMessageV3]])
    } else {
      jsonSerializer.contentTypeFor(clz)
    }
  }

  /**
   * Get all content types for a class.
   */
  def contentTypesFor(clz: Class[_]): List[String] = {
    if (clz == classOf[Array[Byte]]) {
      List(BytesPrimitive.fullName)
    } else if (ProtobufSerializer.isProtobufClass(clz)) {
      ProtobufSerializer.contentTypesFor(clz.asInstanceOf[Class[_ <: GeneratedMessageV3]])
    } else {
      jsonSerializer.contentTypesFor(clz)
    }
  }

  def stripJsonContentTypePrefix(contentType: String): String =
    jsonSerializer.stripJsonContentTypePrefix(contentType)

  def registerTypeHints(clz: Class[_]): Unit = {
    if (ProtobufSerializer.isProtobufClass(clz)) {
      protobufSerializer.registerProtoType(clz.asInstanceOf[Class[_ <: GeneratedMessageV3]])
    } else {
      jsonSerializer.registerTypeHints(clz)
    }
  }
}
