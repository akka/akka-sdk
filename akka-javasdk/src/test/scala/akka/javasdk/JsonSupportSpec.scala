/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk

import java.util.Optional

import akka.util.ByteString
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class JsonSupportSpec extends AnyWordSpec with Matchers {

  private val dummy = new DummyClass("123", 321, Optional.of("test"))
  private val expectedJson = """{"stringValue":"123","intValue":321,"optionalStringValue":"test"}"""

  "JsonSupport" must {

    "expose a stable ObjectMapper instance" in {
      val mapper = JsonSupport.getObjectMapper
      mapper should not be null
      (JsonSupport.getObjectMapper should be).theSameInstanceAs(mapper)
    }

    "encode a value to a JSON String" in {
      JsonSupport.encodeToString(dummy) shouldBe expectedJson
    }

    "encode a value to an Akka ByteString" in {
      JsonSupport.encodeToAkkaByteString(dummy).utf8String shouldBe expectedJson
    }

    "round-trip a value via Akka ByteString" in {
      val bytes = JsonSupport.encodeToAkkaByteString(dummy)
      JsonSupport.decodeJson(classOf[DummyClass], bytes) shouldBe dummy
    }

    "round-trip a value via byte array" in {
      val bytes = JsonSupport.encodeToAkkaByteString(dummy).toArray
      JsonSupport.decodeJson(classOf[DummyClass], bytes) shouldBe dummy
    }

    "decode raw JSON bytes produced outside of encodeTo*" in {
      val raw = ByteString.fromString(expectedJson)
      JsonSupport.decodeJson(classOf[DummyClass], raw) shouldBe dummy
    }

    "deserialize an absent optional field as Optional.empty" in {
      val raw = ByteString.fromString("""{"stringValue":"123","intValue":321}""")
      JsonSupport.decodeJson(classOf[DummyClass], raw) shouldBe new DummyClass("123", 321, Optional.empty())
    }

    "fail to decode malformed JSON with an IllegalArgumentException" in {
      val raw = ByteString.fromString("not json")
      an[IllegalArgumentException] should be thrownBy JsonSupport.decodeJson(classOf[DummyClass], raw)
    }
  }
}
