/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.serialization

import java.util.concurrent.ConcurrentHashMap

import akka.annotation.InternalApi
import akka.javasdk.impl.AnySupport
import akka.javasdk.impl.reflection.Reflect
import akka.runtime.sdk.spi.BytesPayload
import akka.util.ByteString
import com.google.protobuf.GeneratedMessageV3
import com.google.protobuf.util.JsonFormat

/**
 * INTERNAL API
 *
 * Stateful protobuf serializer that maintains a registry of protobuf message types for deserialization without knowing
 * the expected type upfront. Used for event replay in Event Sourced Entities with protobuf events.
 */
@InternalApi
final class ProtobufSerializer {
  import ProtobufSerializer._

  // Maps protobuf full name (e.g., "akkajavasdk.serialization.CustomerCreated") to the Java class
  val reversedTypeHints: ConcurrentHashMap[String, Class[_ <: GeneratedMessageV3]] =
    new ConcurrentHashMap[String, Class[_ <: GeneratedMessageV3]]()

  /**
   * Register a protobuf message type for later deserialization without knowing the expected type. The type is extracted
   * from the protobuf descriptor.
   */
  def registerProtoType(clz: Class[_ <: GeneratedMessageV3]): Unit = {
    val descriptor = Reflect.protoDescriptorFor(clz)
    val fullName = descriptor.getFullName
    reversedTypeHints.put(fullName, clz)
  }

  /**
   * Deserialize bytes to a protobuf message based on the content type. Requires that the type was previously registered
   * via [[registerProtoType]].
   */
  def fromBytes(bytesPayload: BytesPayload): GeneratedMessageV3 = {
    if (!isProtobuf(bytesPayload))
      throw new IllegalArgumentException(
        s"BytesPayload with contentType [${bytesPayload.contentType}] " +
        s"cannot be decoded as protobuf, must start with [$ProtobufContentTypePrefix]")

    val fullName = stripProtobufContentTypePrefix(bytesPayload.contentType)
    val typeClass = reversedTypeHints.get(fullName)
    if (typeClass eq null)
      throw new IllegalStateException(
        s"Cannot decode [${bytesPayload.contentType}] protobuf message type. " +
        s"Class mapping not found. Make sure the protobuf event type is declared in @ProtoEventTypes annotation.")
    else
      AnySupport.decodeJavaProtobuf(bytesPayload, typeClass)
  }

  private def stripProtobufContentTypePrefix(contentType: String): String =
    contentType.stripPrefix(ProtobufContentTypePrefix)
}

/**
 * INTERNAL API
 *
 * Static utility methods for protobuf serialization.
 */
@InternalApi
object ProtobufSerializer {
  val ProtobufContentTypePrefix: String = "type.googleapis.com/"

  // Protobuf JSON printer for converting protobuf to JSON (used by Views)
  private lazy val protobufJsonPrinter: JsonFormat.Printer = JsonFormat
    .printer()
    .preservingProtoFieldNames()
    .omittingInsignificantWhitespace()
    .includingDefaultValueFields()

  // Protobuf JSON parser for converting JSON back to protobuf (used by Views)
  private lazy val protobufJsonParser: JsonFormat.Parser = JsonFormat
    .parser()
    .ignoringUnknownFields()

  def isProtobufClass(clz: Class[_]): Boolean =
    classOf[GeneratedMessageV3].isAssignableFrom(clz)

  def toBytes(value: GeneratedMessageV3): BytesPayload = {
    if (value == null) throw new NullPointerException("Serialized value is null")
    val fullName = value.getDescriptorForType.getFullName
    new BytesPayload(
      bytes = ByteString.fromArrayUnsafe(value.toByteArray),
      contentType = ProtobufContentTypePrefix + fullName)
  }

  /**
   * Serialize a protobuf message to JSON format. This is used for Views which store data as JSONB.
   */
  def toBytesAsJson(value: Any): BytesPayload = {
    if (value == null) throw new NullPointerException("Serialized value is null")

    value match {
      case msg: GeneratedMessageV3 =>
        val jsonString = protobufJsonPrinter.print(msg)
        new BytesPayload(
          bytes = ByteString.fromString(jsonString),
          contentType = JsonSerializer.JsonContentTypePrefix + msg.getDescriptorForType.getFullName)

      case _ =>
        throw new IllegalArgumentException(
          s"ProtobufSerializer can only serialize protobuf messages to JSON, got: ${value.getClass.getName}")
    }
  }

  def fromBytes[T <: GeneratedMessageV3](expectedType: Class[T], bytesPayload: BytesPayload): T = {
    if (!isProtobuf(bytesPayload))
      throw new IllegalArgumentException(
        s"BytesPayload with contentType [${bytesPayload.contentType}] " +
        s"cannot be decoded as protobuf, must start with [$ProtobufContentTypePrefix]")

    if (classOf[GeneratedMessageV3].isAssignableFrom(expectedType)) {
      AnySupport.decodeJavaProtobuf(bytesPayload, expectedType)
    } else {
      throw new IllegalArgumentException(s"Not a protobuf class: ${expectedType.getName}")
    }
  }

  private val builderMethodCache = new ConcurrentHashMap[Class[_], () => com.google.protobuf.Message.Builder]()
  private def newBuilderFor(messageClass: Class[_ <: GeneratedMessageV3]): com.google.protobuf.Message.Builder = {
    val newBuilder = builderMethodCache.computeIfAbsent(
      messageClass,
      { clazz =>
        // reflective method lookup once
        val newBuilderMethod = clazz.getMethod("newBuilder")

        // reuse to construct
        () => newBuilderMethod.invoke(null).asInstanceOf[com.google.protobuf.Message.Builder]
      })
    newBuilder()
  }

  /**
   * Deserialize a protobuf message from JSON format. Used for Views which store and return data as JSONB.
   */
  def fromBytesAsJson[T <: GeneratedMessageV3](expectedType: Class[T], bytesPayload: BytesPayload): T = {
    val builder = newBuilderFor(expectedType)
    val jsonString = bytesPayload.bytes.utf8String
    protobufJsonParser.merge(jsonString, builder)
    builder.build().asInstanceOf[T]
  }

  def isProtobuf(bytesPayload: BytesPayload): Boolean =
    isProtobufContentType(bytesPayload.contentType)

  def isProtobufContentType(contentType: String): Boolean =
    contentType.startsWith(ProtobufContentTypePrefix)

  def contentTypeFor(clz: Class[_ <: GeneratedMessageV3]): String = {
    val descriptor = Reflect.protoDescriptorFor(clz)
    ProtobufContentTypePrefix + descriptor.getFullName
  }

  def contentTypesFor(clz: Class[_ <: GeneratedMessageV3]): List[String] = {
    List(contentTypeFor(clz))
  }

}
