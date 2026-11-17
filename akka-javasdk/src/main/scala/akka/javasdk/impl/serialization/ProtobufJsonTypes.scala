/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.serialization

import akka.annotation.InternalApi

/**
 * INTERNAL API
 *
 * Full names of the protobuf well-known types that `JsonFormat` prints in a special form.
 */
@InternalApi
private[javasdk] object ProtobufJsonTypes {

  val TimestampType: String = "google.protobuf.Timestamp"

  /** Printed as the JSON value of their `value` field. */
  val LongWrapperTypes: Set[String] = Set("google.protobuf.Int64Value", "google.protobuf.UInt64Value")

  val WrapperTypes: Set[String] = LongWrapperTypes ++ Set(
    "google.protobuf.DoubleValue",
    "google.protobuf.FloatValue",
    "google.protobuf.Int32Value",
    "google.protobuf.UInt32Value",
    "google.protobuf.BoolValue",
    "google.protobuf.StringValue",
    "google.protobuf.BytesValue")

  /** Printed as a JSON string. */
  val StringJsonTypes: Set[String] = Set("google.protobuf.Duration", "google.protobuf.FieldMask")

  /** Printed as arbitrary JSON, without a fixed schema. */
  val UnsupportedTypes: Set[String] =
    Set("google.protobuf.Struct", "google.protobuf.Value", "google.protobuf.ListValue", "google.protobuf.Any")

  /** All types with a special JSON form. */
  val SpecialTypes: Set[String] = WrapperTypes ++ StringJsonTypes ++ UnsupportedTypes + TimestampType
}
