/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.serialization

import akka.runtime.sdk.spi.BytesPayload
import akka.util.ByteString
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
