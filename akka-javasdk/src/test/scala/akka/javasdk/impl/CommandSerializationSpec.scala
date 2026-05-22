/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.javasdk.JsonSupport
import akka.javasdk.impl.serialization.JsonSerializer
import akka.javasdk.impl.serialization.Serializer
import akka.runtime.sdk.spi.BytesPayload
import akka.util.ByteString
import org.scalatest.TestSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

object CommandSerializationSpec {
  // test classes need to be defined outside the test class for reflection to work
  class StringParamHandler {
    def handle(message: String): String = message
  }

  class IntParamHandler {
    def handle(count: java.lang.Integer): java.lang.Integer = count
  }

  case class MyRecord(name: String, value: Int)

  class RecordParamHandler {
    def handle(request: MyRecord): MyRecord = request
  }

  class NoParamHandler {
    def handle(): String = "ok"
  }
}

class CommandSerializationSpec extends AnyWordSpecLike with TestSuite with Matchers {
  import CommandSerializationSpec._

  private val serializer = new Serializer(JsonSupport.getObjectMapper)
  private val untypedObjectContentType = JsonSerializer.JsonContentTypePrefix + "object"

  private def method(cls: Class[_]) =
    cls.getMethods.find(_.getName == "handle").get

  private def untypedJsonPayload(json: String): BytesPayload =
    new BytesPayload(ByteString.fromString(json), untypedObjectContentType)

  private def typedJsonPayload(json: String, typeName: String): BytesPayload =
    new BytesPayload(ByteString.fromString(json), JsonSerializer.JsonContentTypePrefix + typeName)

  "CommandSerialization" should {

    "unwrap String parameter from untyped JSON object" in {
      val m = method(classOf[StringParamHandler])
      val payload = untypedJsonPayload("""{"message":"hello world"}""")

      val result = CommandSerialization.deserializeComponentClientCommand(m, payload, serializer)

      result shouldBe Some("hello world")
    }

    "unwrap Integer parameter from untyped JSON object" in {
      val m = method(classOf[IntParamHandler])
      val payload = untypedJsonPayload("""{"count":42}""")

      val result = CommandSerialization.deserializeComponentClientCommand(m, payload, serializer)

      result shouldBe Some(42)
    }

    "fail with clear error when expected property is missing from untyped JSON object" in {
      val m = method(classOf[StringParamHandler])
      val payload = untypedJsonPayload("""{"wrongName":"hello"}""")

      val ex = intercept[IllegalArgumentException] {
        CommandSerialization.deserializeComponentClientCommand(m, payload, serializer)
      }
      ex.getMessage should include("message")
    }

    "unwrap record parameter from untyped JSON object" in {
      // LLM wraps the record under the parameter name: {"request": {"name": "test", "value": 123}}
      val m = method(classOf[RecordParamHandler])
      val payload = untypedJsonPayload("""{"request":{"name":"test","value":123}}""")

      val result = CommandSerialization.deserializeComponentClientCommand(m, payload, serializer)

      result shouldBe Some(MyRecord("test", 123))
    }

    "deserialize String parameter from typed JSON payload (normal component client path)" in {
      // Typed content type (from normal componentClient calls) should use existing path
      val m = method(classOf[StringParamHandler])
      val payload = typedJsonPayload("\"hello world\"", classOf[String].getName)

      val result = CommandSerialization.deserializeComponentClientCommand(m, payload, serializer)

      result shouldBe Some("hello world")
    }

    "return None for no-param handler" in {
      val m = method(classOf[NoParamHandler])
      val payload = BytesPayload.empty

      val result = CommandSerialization.deserializeComponentClientCommand(m, payload, serializer)

      result shouldBe None
    }
  }
}
