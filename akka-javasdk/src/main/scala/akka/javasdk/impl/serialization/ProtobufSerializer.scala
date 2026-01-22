/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.serialization

import akka.annotation.InternalApi
import akka.javasdk.impl.AnySupport
import akka.runtime.sdk.spi.BytesPayload
import akka.util.ByteString
import com.google.protobuf.GeneratedMessageV3
import com.google.protobuf.util.JsonFormat

/**
 * INTERNAL API
 */
@InternalApi
object ProtobufSerializer {
  val ProtobufContentTypePrefix: String = "type.googleapis.com/"

  // Protobuf JSON printer for converting protobuf to JSON (used by Views)
  private lazy val protobufJsonPrinter: JsonFormat.Printer = JsonFormat
    .printer()
    .preservingProtoFieldNames()
    .omittingInsignificantWhitespace()

  def isProtobufClass(clz: Class[_]): Boolean =
    classOf[GeneratedMessageV3].isAssignableFrom(clz)

  def toBytes(value: GeneratedMessageV3): BytesPayload = {
    if (value == null) throw new NullPointerException("Serialized value is null")

    value match {
      case msg: GeneratedMessageV3 =>
        val fullName = msg.getDescriptorForType.getFullName
        new BytesPayload(
          bytes = ByteString.fromArrayUnsafe(msg.toByteArray),
          contentType = ProtobufContentTypePrefix + fullName)

      case _ =>
        throw new IllegalArgumentException(
          s"ProtobufSerializer can only serialize protobuf messages, got: ${value.getClass.getName}")
    }
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

  def isProtobuf(bytesPayload: BytesPayload): Boolean =
    isProtobufContentType(bytesPayload.contentType)

  def isProtobufContentType(contentType: String): Boolean =
    contentType.startsWith(ProtobufContentTypePrefix)

  def contentTypeFor(clz: Class[_ <: GeneratedMessageV3]): String = {
    val descriptor = clz
      .getMethod("getDescriptor")
      .invoke(null)
      .asInstanceOf[com.google.protobuf.Descriptors.Descriptor]
    ProtobufContentTypePrefix + descriptor.getFullName
  }

  def contentTypesFor(clz: Class[_ <: GeneratedMessageV3]): List[String] = {
    List(contentTypeFor(clz))
  }

}
