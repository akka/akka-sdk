/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.serialization

import akka.runtime.sdk.spi.BytesPayload
import akka.util.ByteString
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.protobuf.Int64Value
import com.google.protobuf.Timestamp
import com.google.protobuf.UInt64Value
import numbers.NumberProtos.NoLongFields
import numbers.NumberProtos.NumberFields
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import protoconsumer.EventsForConsumer

class ProtobufSerializerSpec extends AnyWordSpec with Matchers {

  "The ProtobufSerializer" should {

    "serialize and deserialize Java protobuf messages" in {
      val original = EventsForConsumer.EventForConsumer1.newBuilder().setText("hello world").build()

      val bytesPayload = ProtobufSerializer.toBytes(original)

      bytesPayload.contentType shouldBe "type.googleapis.com/protoconsumer.EventForConsumer1"
      bytesPayload.bytes should not be empty

      val deserialized = ProtobufSerializer.fromBytes(classOf[EventsForConsumer.EventForConsumer1], bytesPayload)
      deserialized shouldBe original
      deserialized.getText shouldBe "hello world"
    }

    "serialize different Java protobuf message types" in {
      val event1 = EventsForConsumer.EventForConsumer1.newBuilder().setText("event1").build()
      val event2 = EventsForConsumer.EventForConsumer2.newBuilder().setText("event2").build()

      val payload1 = ProtobufSerializer.toBytes(event1)
      val payload2 = ProtobufSerializer.toBytes(event2)

      payload1.contentType shouldBe "type.googleapis.com/protoconsumer.EventForConsumer1"
      payload2.contentType shouldBe "type.googleapis.com/protoconsumer.EventForConsumer2"

      ProtobufSerializer.fromBytes(classOf[EventsForConsumer.EventForConsumer1], payload1) shouldBe event1
      ProtobufSerializer.fromBytes(classOf[EventsForConsumer.EventForConsumer2], payload2) shouldBe event2
    }

    "serialize Java protobuf message to JSON format" in {
      val original = EventsForConsumer.EventForConsumer1.newBuilder().setText("hello json").build()

      val bytesPayload = ProtobufSerializer.toBytesAsJson(original)

      bytesPayload.contentType shouldBe "json.akka.io/protoconsumer.EventForConsumer1"
      val jsonString = bytesPayload.bytes.utf8String
      jsonString should include("text")
      jsonString should include("hello json")
    }

    "serialize 64-bit integers as JSON numbers in view JSON" in {
      val maxUnsigned = java.lang.Long.parseUnsignedLong("18446744073709551615")
      val nested = NumberFields.newBuilder().setInt64Value(Long.MinValue).build()
      val original = NumberFields
        .newBuilder()
        .setInt32Value(1)
        .setUint32Value(Integer.parseUnsignedInt("4294967295"))
        .setFixed32Value(Integer.parseUnsignedInt("3000000000"))
        .setInt64Value(Long.MaxValue)
        .setSint64Value(-5L)
        .setSfixed64Value(6L)
        .setUint64Value(maxUnsigned)
        .setFixed64Value(maxUnsigned)
        .addInt64Values(7L)
        .addInt64Values(8L)
        .putInt64Map("nine", 9L)
        .setInt64Wrapper(Int64Value.of(10L))
        .setUint64Wrapper(UInt64Value.of(maxUnsigned))
        .setNested(nested)
        .addNestedList(nested)
        .setTimestamp(Timestamp.newBuilder().setSeconds(11L))
        .setText("12")
        .build()

      val bytesPayload = ProtobufSerializer.toBytesAsViewJson(original)

      bytesPayload.contentType shouldBe "json.akka.io/numbers.NumberFields"
      val json = new ObjectMapper().readTree(bytesPayload.bytes.utf8String)
      json.get("int32_value").isNumber shouldBe true
      json.get("uint32_value").asText shouldBe "4294967295"
      json.get("uint32_value").isNumber shouldBe true
      json.get("fixed32_value").asText shouldBe "3000000000"
      json.get("int64_value").isNumber shouldBe true
      json.get("int64_value").asLong shouldBe Long.MaxValue
      json.get("sint64_value").asLong shouldBe -5L
      json.get("sfixed64_value").asLong shouldBe 6L
      json.get("uint64_value").isNumber shouldBe true
      json.get("uint64_value").bigIntegerValue shouldBe new java.math.BigInteger("18446744073709551615")
      json.get("fixed64_value").bigIntegerValue shouldBe new java.math.BigInteger("18446744073709551615")
      json.get("int64_values").get(0).isNumber shouldBe true
      json.get("int64_values").get(1).asLong shouldBe 8L
      json.get("int64_map").get("nine").isNumber shouldBe true
      json.get("int64_wrapper").isNumber shouldBe true
      json.get("int64_wrapper").asLong shouldBe 10L
      json.get("uint64_wrapper").bigIntegerValue shouldBe new java.math.BigInteger("18446744073709551615")
      json.get("nested").get("int64_value").asLong shouldBe Long.MinValue
      json.get("nested").get("int64_value").isNumber shouldBe true
      json.get("nested_list").get(0).get("int64_value").isNumber shouldBe true
      json.get("timestamp").isTextual shouldBe true
      json.get("text").isTextual shouldBe true

      ProtobufSerializer.fromBytesAsJson(classOf[NumberFields], bytesPayload) shouldBe original
    }

    "serialize a 64-bit wrapper type as a JSON number in view JSON" in {
      val bytesPayload = ProtobufSerializer.toBytesAsViewJson(Int64Value.of(42L))
      bytesPayload.bytes.utf8String shouldBe "42"
      ProtobufSerializer.fromBytesAsJson(classOf[Int64Value], bytesPayload) shouldBe Int64Value.of(42L)
    }

    "serialize a message without 64-bit integer fields to the same view JSON as regular JSON" in {
      val original = NoLongFields.newBuilder().setText("text").setCount(3).build()
      ProtobufSerializer.toBytesAsViewJson(original) shouldBe ProtobufSerializer.toBytesAsJson(original)
    }

    "detect protobuf content type" in {
      val protoPayload = new BytesPayload(ByteString.empty, "type.googleapis.com/some.Message")
      val jsonPayload = new BytesPayload(ByteString.empty, "json.akka.io/some.Type")

      ProtobufSerializer.isProtobuf(protoPayload) shouldBe true
      ProtobufSerializer.isProtobuf(jsonPayload) shouldBe false

      ProtobufSerializer.isProtobufContentType("type.googleapis.com/some.Message") shouldBe true
      ProtobufSerializer.isProtobufContentType("json.akka.io/some.Type") shouldBe false
    }

    "get content type for Java protobuf class" in {
      val contentType = ProtobufSerializer.contentTypeFor(classOf[EventsForConsumer.EventForConsumer1])
      contentType shouldBe "type.googleapis.com/protoconsumer.EventForConsumer1"
    }

    "get content types list for Java protobuf class" in {
      val contentTypes = ProtobufSerializer.contentTypesFor(classOf[EventsForConsumer.EventForConsumer1])
      contentTypes should contain("type.googleapis.com/protoconsumer.EventForConsumer1")
    }

    "throw exception when serializing null" in {
      an[RuntimeException] should be thrownBy ProtobufSerializer.toBytes(null)
    }

    "throw exception when deserializing with wrong content type" in {
      val jsonPayload = new BytesPayload(ByteString("{}"), "json.akka.io/some.Type")
      an[IllegalArgumentException] should be thrownBy {
        ProtobufSerializer.fromBytes(classOf[EventsForConsumer.EventForConsumer1], jsonPayload)
      }
    }

    "handle empty protobuf message" in {
      val empty = EventsForConsumer.EventForConsumer1.newBuilder().build()

      val bytesPayload = ProtobufSerializer.toBytes(empty)
      val deserialized = ProtobufSerializer.fromBytes(classOf[EventsForConsumer.EventForConsumer1], bytesPayload)

      deserialized.getText shouldBe ""
    }

  }
}
