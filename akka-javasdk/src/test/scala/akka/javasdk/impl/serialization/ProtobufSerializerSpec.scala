/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.serialization

import akka.runtime.sdk.spi.BytesPayload
import akka.util.ByteString
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import protoconsumer.EventsForConsumer

class ProtobufSerializerSpec extends AnyWordSpec with Matchers {

  private val serializer = new ProtobufSerializer

  "The ProtobufSerializer" should {

    "serialize and deserialize Java protobuf messages" in {
      val original = EventsForConsumer.EventForConsumer1.newBuilder().setText("hello world").build()

      val bytesPayload = serializer.toBytes(original)

      bytesPayload.contentType shouldBe "type.googleapis.com/protoconsumer.EventForConsumer1"
      bytesPayload.bytes should not be empty

      val deserialized = serializer.fromBytes(classOf[EventsForConsumer.EventForConsumer1], bytesPayload)
      deserialized shouldBe original
      deserialized.getText shouldBe "hello world"
    }

    "serialize different Java protobuf message types" in {
      val event1 = EventsForConsumer.EventForConsumer1.newBuilder().setText("event1").build()
      val event2 = EventsForConsumer.EventForConsumer2.newBuilder().setText("event2").build()

      val payload1 = serializer.toBytes(event1)
      val payload2 = serializer.toBytes(event2)

      payload1.contentType shouldBe "type.googleapis.com/protoconsumer.EventForConsumer1"
      payload2.contentType shouldBe "type.googleapis.com/protoconsumer.EventForConsumer2"

      serializer.fromBytes(classOf[EventsForConsumer.EventForConsumer1], payload1) shouldBe event1
      serializer.fromBytes(classOf[EventsForConsumer.EventForConsumer2], payload2) shouldBe event2
    }

    "serialize Java protobuf message to JSON format" in {
      val original = EventsForConsumer.EventForConsumer1.newBuilder().setText("hello json").build()

      val bytesPayload = serializer.toBytesAsJson(original)

      bytesPayload.contentType shouldBe "json.akka.io/protoconsumer.EventForConsumer1"
      val jsonString = bytesPayload.bytes.utf8String
      jsonString should include("text")
      jsonString should include("hello json")
    }

    "detect protobuf content type" in {
      val protoPayload = new BytesPayload(ByteString.empty, "type.googleapis.com/some.Message")
      val jsonPayload = new BytesPayload(ByteString.empty, "json.akka.io/some.Type")

      serializer.isProtobuf(protoPayload) shouldBe true
      serializer.isProtobuf(jsonPayload) shouldBe false

      serializer.isProtobufContentType("type.googleapis.com/some.Message") shouldBe true
      serializer.isProtobufContentType("json.akka.io/some.Type") shouldBe false
    }

    "get content type for Java protobuf class" in {
      val contentType = serializer.contentTypeFor(classOf[EventsForConsumer.EventForConsumer1])
      contentType shouldBe "type.googleapis.com/protoconsumer.EventForConsumer1"
    }

    "get content types list for Java protobuf class" in {
      val contentTypes = serializer.contentTypesFor(classOf[EventsForConsumer.EventForConsumer1])
      contentTypes should contain("type.googleapis.com/protoconsumer.EventForConsumer1")
    }

    "throw exception when serializing null" in {
      an[RuntimeException] should be thrownBy serializer.toBytes(null)
    }

    "throw exception when serializing non-protobuf type" in {
      an[IllegalArgumentException] should be thrownBy serializer.toBytes("not a protobuf")
    }

    "throw exception when deserializing with wrong content type" in {
      val jsonPayload = new BytesPayload(ByteString("{}"), "json.akka.io/some.Type")
      an[IllegalArgumentException] should be thrownBy {
        serializer.fromBytes(classOf[EventsForConsumer.EventForConsumer1], jsonPayload)
      }
    }

    "throw exception when deserializing to non-protobuf class" in {
      val protoPayload = new BytesPayload(ByteString.empty, "type.googleapis.com/some.Message")
      an[IllegalArgumentException] should be thrownBy {
        serializer.fromBytes(classOf[String], protoPayload)
      }
    }

    "throw exception for contentTypeFor with non-protobuf class" in {
      an[IllegalArgumentException] should be thrownBy {
        serializer.contentTypeFor(classOf[String])
      }
    }

    "handle empty protobuf message" in {
      val empty = EventsForConsumer.EventForConsumer1.newBuilder().build()

      val bytesPayload = serializer.toBytes(empty)
      val deserialized = serializer.fromBytes(classOf[EventsForConsumer.EventForConsumer1], bytesPayload)

      deserialized.getText shouldBe ""
    }

  }
}
