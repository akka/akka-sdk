/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.serialization

import akka.runtime.sdk.spi.BytesPayload
import akka.util.ByteString
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import protoconsumer.EventsForConsumer

// Top-level case class for Jackson deserialization
case class JsonTestMessage(name: String, value: Int)

/**
 * Tests for the composite Serializer that auto-detects between JSON and Protobuf serialization.
 */
class SerializerSpec extends AnyWordSpec with Matchers {

  private val serializer = new Serializer()

  "The Serializer" should {

    "auto-detect and serialize protobuf messages to binary format" in {
      val proto = EventsForConsumer.EventForConsumer1.newBuilder().setText("test").build()

      val bytesPayload = serializer.toBytes(proto)

      bytesPayload.contentType shouldBe "type.googleapis.com/protoconsumer.EventForConsumer1"
      serializer.isProtobuf(bytesPayload) shouldBe true
      serializer.isJson(bytesPayload) shouldBe false
    }

    "auto-detect and serialize non-protobuf objects to JSON format" in {
      val json = JsonTestMessage("test", 42)

      serializer.json.registerTypeHints(classOf[JsonTestMessage])
      val bytesPayload = serializer.toBytes(json)

      bytesPayload.contentType should startWith("json.akka.io/")
      serializer.isJson(bytesPayload) shouldBe true
      serializer.isProtobuf(bytesPayload) shouldBe false
    }

    "deserialize protobuf message from binary format" in {
      val original = EventsForConsumer.EventForConsumer1.newBuilder().setText("hello").build()
      val bytesPayload = serializer.toBytes(original)

      val deserialized = serializer.fromBytes(classOf[EventsForConsumer.EventForConsumer1], bytesPayload)

      deserialized shouldBe original
    }

    "deserialize JSON object from JSON format" in {
      val original = JsonTestMessage("json test", 123)
      serializer.json.registerTypeHints(classOf[JsonTestMessage])
      val bytesPayload = serializer.toBytes(original)

      val deserialized = serializer.fromBytes(classOf[JsonTestMessage], bytesPayload)

      deserialized shouldBe original
    }

    "serialize protobuf to JSON when requested via toBytesAsJson" in {
      val proto = EventsForConsumer.EventForConsumer1.newBuilder().setText("json output").build()

      val bytesPayload = serializer.toBytesAsJson(proto)

      bytesPayload.contentType shouldBe "json.akka.io/protoconsumer.EventForConsumer1"
      val jsonString = bytesPayload.bytes.utf8String
      jsonString should include("text")
      jsonString should include("json output")
    }

    "return correct content type for protobuf class" in {
      val contentType = serializer.contentTypeFor(classOf[EventsForConsumer.EventForConsumer1])
      contentType shouldBe "type.googleapis.com/protoconsumer.EventForConsumer1"
    }

    "return correct content type for JSON class" in {
      serializer.json.registerTypeHints(classOf[JsonTestMessage])
      val contentType = serializer.contentTypeFor(classOf[JsonTestMessage])
      contentType should startWith("json.akka.io/")
    }

    "return correct content types list for protobuf class" in {
      val contentTypes = serializer.contentTypesFor(classOf[EventsForConsumer.EventForConsumer1])
      contentTypes should contain("type.googleapis.com/protoconsumer.EventForConsumer1")
    }

    "throw exception when serializing null" in {
      an[NullPointerException] should be thrownBy serializer.toBytes(null)
    }

    "throw exception when deserializing protobuf from wrong content type" in {
      val jsonPayload = new BytesPayload(ByteString("{}"), "json.akka.io/some.Type")

      an[IllegalArgumentException] should be thrownBy {
        serializer.fromBytes(classOf[EventsForConsumer.EventForConsumer1], jsonPayload)
      }
    }

    "expose json serializer for direct access" in {
      serializer.json shouldBe a[JsonSerializer]
    }

    "round-trip multiple protobuf message types" in {
      val event1 = EventsForConsumer.EventForConsumer1.newBuilder().setText("event1").build()
      val event2 = EventsForConsumer.EventForConsumer2.newBuilder().setText("event2").build()

      val payload1 = serializer.toBytes(event1)
      val payload2 = serializer.toBytes(event2)

      payload1.contentType should not be payload2.contentType

      serializer.fromBytes(classOf[EventsForConsumer.EventForConsumer1], payload1) shouldBe event1
      serializer.fromBytes(classOf[EventsForConsumer.EventForConsumer2], payload2) shouldBe event2
    }
  }
}
