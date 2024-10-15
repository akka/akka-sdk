/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.annotation.InternalApi
import com.google.protobuf.ByteString
import com.google.protobuf.Descriptors
import com.google.protobuf.Parser
import com.google.protobuf.{ Message => JavaMessage }

/**
 * A resolved service method.
 *
 * INTERNAL API
 */
@InternalApi
final case class ResolvedServiceMethod[I, O](
    descriptor: Descriptors.MethodDescriptor,
    inputType: ResolvedType[I],
    outputType: ResolvedType[O]) {

  def outputStreamed: Boolean = descriptor.isServerStreaming
  def name: String = descriptor.getName

  def method(): Descriptors.MethodDescriptor = descriptor
}

/**
 * A resolved type
 *
 * INTERNAL API
 */
@InternalApi
trait ResolvedType[T] {

  /**
   * Parse the given bytes into this type.
   */
  def parseFrom(bytes: ByteString): T

}

private final class JavaPbResolvedType[T <: JavaMessage](parser: Parser[T]) extends ResolvedType[T] {
  override def parseFrom(bytes: ByteString): T = parser.parseFrom(bytes)
}

private final class ScalaPbResolvedType[T <: scalapb.GeneratedMessage](companion: scalapb.GeneratedMessageCompanion[_])
    extends ResolvedType[T] {
  override def parseFrom(bytes: ByteString): T = companion.parseFrom(bytes.newCodedInput()).asInstanceOf[T]
}
