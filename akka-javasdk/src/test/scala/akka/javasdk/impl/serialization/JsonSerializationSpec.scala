/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.serialization

import java.util
import java.util.Base64
import java.util.Optional

import scala.beans.BeanProperty

import akka.Done
import akka.javasdk.DummyClass
import akka.javasdk.DummyClass2
import akka.javasdk.DummyClassRenamed
import akka.javasdk.JsonMigration
import akka.javasdk.UserException
import akka.javasdk.annotations.Migration
import akka.javasdk.annotations.TypeName
import akka.javasdk.impl.serialization
import akka.javasdk.impl.serialization.JsonSerializationSpec.Cat
import akka.javasdk.impl.serialization.JsonSerializationSpec.Dog
import akka.javasdk.impl.serialization.JsonSerializationSpec.ExceptionData
import akka.javasdk.impl.serialization.JsonSerializationSpec.OtherUserException
import akka.javasdk.impl.serialization.JsonSerializationSpec.SimpleClass
import akka.javasdk.impl.serialization.JsonSerializationSpec.SimpleClassUpdated
import akka.javasdk.impl.serialization.JsonSerializationSpec.TestUserException
import akka.runtime.sdk.spi.BytesPayload
import akka.util.ByteString
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.IntNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

object JsonSerializationSpec {
  class MyJsonable {
    @BeanProperty var field: String = _
  }

  @JsonCreator
  @TypeName("animal")
  final case class Dog(str: String)

  @JsonCreator
  @TypeName("animal")
  final case class Cat(str: String)

  @JsonCreator
  final case class ExceptionData(stringField: String, intField: Int)

  @JsonCreator
  final case class TestUserException(msg: String, exceptionData: ExceptionData) extends UserException(msg)

  @JsonCreator
  final case class OtherUserException(msg: String, exceptionData: ExceptionData) extends UserException(msg)

  @JsonCreator
  final case class NotUserException(msg: String, exceptionData: ExceptionData)

  @JsonCreator
  case class SimpleClass(str: String, in: Int)

  class SimpleClassUpdatedMigration extends JsonMigration {
    override def currentVersion(): Int = 1
    override def transform(fromVersion: Int, jsonNode: JsonNode): JsonNode = {
      if (fromVersion == 0) {
        jsonNode.asInstanceOf[ObjectNode].set("newField", IntNode.valueOf(1))
      } else {
        jsonNode
      }
    }

    override def supportedClassNames(): util.List[String] = {
      util.List.of(classOf[SimpleClass].getName)
    }
  }

  @JsonCreator
  @Migration(classOf[SimpleClassUpdatedMigration])
  final case class SimpleClassUpdated(str: String, in: Int, newField: Int)

  object AnnotatedWithTypeName {

    sealed trait Animal

    @TypeName("lion")
    final case class Lion(name: String) extends Animal

    @TypeName("elephant")
    final case class Elephant(name: String, age: Int) extends Animal

    @TypeName("elephant")
    final case class IndianElephant(name: String, age: Int) extends Animal
  }

  object AnnotatedWithEmptyTypeName {

    sealed trait Animal

    @TypeName("")
    final case class Lion(name: String) extends Animal

    @TypeName(" ")
    final case class Elephant(name: String, age: Int) extends Animal
  }

  final case class SomeTypeWithOptional(optional: Optional[String])

}
class JsonSerializationSpec extends AnyWordSpec with Matchers {
  import JsonSerializationSpec.MyJsonable

  private def jsonContentTypeWith(typ: String) = JsonSerializer.JsonContentTypePrefix + typ

  private val serializer = new JsonSerializer

  private val myJsonable = new MyJsonable
  myJsonable.field = "foo"

  "The JsonSerializer" should {

    "serialize and deserialize JSON" in {
      val bytesPayload = serializer.toBytes(myJsonable)
      bytesPayload.contentType shouldBe jsonContentTypeWith(classOf[MyJsonable].getName)
      serializer.fromBytes(classOf[MyJsonable], bytesPayload).field shouldBe "foo"
    }

    "serialize and deserialize DummyClass" in {
      val dummy = new DummyClass("123", 321, Optional.of("test"))
      val bytesPayload = serializer.toBytes(dummy)
      bytesPayload.contentType shouldBe jsonContentTypeWith(classOf[DummyClass].getName)
      val decoded = serializer.fromBytes(classOf[DummyClass], bytesPayload)
      decoded shouldBe dummy
    }

    "deserialize missing field as optional none" in {
      val bytesPayload = new BytesPayload(
        ByteString.fromString("""{"stringValue":"123","intValue":321}"""),
        jsonContentTypeWith(classOf[DummyClass].getName))
      val decoded = serializer.fromBytes(classOf[DummyClass], bytesPayload)
      decoded shouldBe new DummyClass("123", 321, Optional.empty())
    }

    "deserialize null field as optional none" in {
      val bytesPayload = new BytesPayload(
        ByteString.fromString("""{"stringValue":"123","intValue":321,"optionalStringValue":null}"""),
        jsonContentTypeWith(classOf[DummyClass].getName))
      val decoded = serializer.fromBytes(classOf[DummyClass], bytesPayload)
      decoded shouldBe new DummyClass("123", 321, Optional.empty())
    }

    "deserialize mandatory field with migration" in {
      val bytesPayload = new BytesPayload(
        ByteString.fromString("""{"stringValue":"123","intValue":321}"""),
        jsonContentTypeWith(classOf[DummyClass2].getName))
      val decoded = serializer.fromBytes(classOf[DummyClass2], bytesPayload)
      decoded shouldBe new DummyClass2("123", 321, "mandatory-value")
    }

    "deserialize renamed class" in {
      val bytesPayload = new BytesPayload(
        ByteString.fromString("""{"stringValue":"123","intValue":321}"""),
        jsonContentTypeWith(classOf[DummyClass].getName))
      val decoded = serializer.fromBytes(classOf[DummyClassRenamed], bytesPayload)
      decoded shouldBe new DummyClassRenamed("123", 321, Optional.empty())
    }

    "deserialize forward from DummyClass2 to DummyClass" in {
      val bytesPayload = new BytesPayload(
        ByteString.fromString("""{"stringValue":"123","intValue":321,"mandatoryStringValue":"value"}"""),
        jsonContentTypeWith(classOf[DummyClass2].getName + "#1"))
      val decoded = serializer.fromBytes(classOf[DummyClass], bytesPayload)
      decoded shouldBe new DummyClass("123", 321, Optional.of("value"))
    }

    "serialize and deserialize Akka Done class" in {
      val bytesPayload = serializer.toBytes(Done.getInstance())
      bytesPayload.contentType shouldBe jsonContentTypeWith(Done.getClass.getName)
      serializer.fromBytes(classOf[Done], bytesPayload) shouldBe Done.getInstance()
    }

    "serialize and deserialize a List of objects" in {
      val customers: java.util.List[MyJsonable] = new util.ArrayList[MyJsonable]()
      val foo = new MyJsonable
      foo.field = "foo"
      customers.add(foo)

      val bar = new MyJsonable
      bar.field = "bar"
      customers.add(bar)
      val bytesPayload = serializer.toBytes(customers)

      val decodedCustomers =
        serializer.fromBytes(classOf[MyJsonable], classOf[java.util.List[MyJsonable]], bytesPayload)
      decodedCustomers.get(0).field shouldBe "foo"
      decodedCustomers.get(1).field shouldBe "bar"
    }

    "serialize JSON with an explicit type url suffix" in {
      pending // FIXME do we need this? see JsonSupportSpec
    }

    "conditionally decode JSON depending on suffix" in {
      pending // FIXME do we need this? see JsonSupportSpec
    }

    "serialize deserialize exception" in {
      val exceptionData = ExceptionData("test", 123)
      val exception = TestUserException("test exception", exceptionData)
      val bytesPayload = serializer.toBytes(exception)
      bytesPayload.contentType shouldBe jsonContentTypeWith(exception.getClass.getName)
      val decoded = serializer.exceptionFromBytes(bytesPayload).asInstanceOf[TestUserException]
      decoded.getMessage shouldBe "test exception"
      decoded.exceptionData shouldBe exceptionData
      decoded.getStackTrace shouldBe empty
      decoded.getCause shouldBe null
    }

    "deserialize exception with empty reversed cache" in {
      val exceptionData = ExceptionData("test", 123)
      // payload: {"msg":"test exception","exceptionData":{"stringField":"test","intField":123},"message":"test exception"}
      val rawPayload =
        Base64.getDecoder.decode(
          "eyJtc2ciOiJ0ZXN0IGV4Y2VwdGlvbiIsImV4Y2VwdGlvbkRhdGEiOnsic3RyaW5nRmllbGQiOiJ0ZXN0IiwiaW50RmllbGQiOjEyM30sIm1lc3NhZ2UiOiJ0ZXN0IGV4Y2VwdGlvbiJ9")
      val bytesPayload = new BytesPayload(
        ByteString(rawPayload),
        "json.akka.io/akka.javasdk.impl.serialization.JsonSerializationSpec$OtherUserException")
      val decoded = serializer.exceptionFromBytes(bytesPayload).asInstanceOf[OtherUserException]
      decoded.getMessage shouldBe "test exception"
      decoded.exceptionData shouldBe exceptionData
    }

    "not deserialize exception that is not UserException" in {
      // payload: {"msg":"test exception","exceptionData":{"stringField":"test","intField":123},"message":"test exception"}
      val rawPayload =
        Base64.getDecoder.decode(
          "eyJtc2ciOiJ0ZXN0IGV4Y2VwdGlvbiIsImV4Y2VwdGlvbkRhdGEiOnsic3RyaW5nRmllbGQiOiJ0ZXN0IiwiaW50RmllbGQiOjEyM30sIm1lc3NhZ2UiOiJ0ZXN0IGV4Y2VwdGlvbiJ9")
      val bytesPayload = new BytesPayload(
        ByteString(rawPayload),
        "json.akka.io/akka.javasdk.impl.serialization.JsonSerializationSpec$NotUserException")
      val caught = intercept[IllegalStateException] {
        serializer.exceptionFromBytes(bytesPayload)
      }
      caught.getMessage shouldBe "Loaded exception class [akka.javasdk.impl.serialization.JsonSerializationSpec$NotUserException] is not a subclass of UserException, cannot deserialize it as a UserException."
    }

    "not deserialize exception that unknown" in {
      val bytesPayload = new BytesPayload(
        ByteString("rawPayload"),
        "json.akka.io/akka.javasdk.impl.serialization.JsonSerializationSpec$UnknownException")
      val caught = intercept[ClassNotFoundException] {
        serializer.exceptionFromBytes(bytesPayload)
      }
      caught.getMessage shouldBe "akka.javasdk.impl.serialization.JsonSerializationSpec$UnknownException"
    }

    "support java primitives" in {
      val integer = serializer.toBytes(123)
      integer.contentType shouldBe jsonContentTypeWith("int")
      serializer.fromBytes(integer) shouldBe 123
      serializer.fromBytes(new BytesPayload(integer.bytes, jsonContentTypeWith("java.lang.Integer"))) shouldBe 123

      val long = serializer.toBytes(123L)
      long.contentType shouldBe jsonContentTypeWith("long")
      serializer.fromBytes(long) shouldBe 123L
      serializer.fromBytes(new BytesPayload(long.bytes, jsonContentTypeWith("java.lang.Long"))) shouldBe 123L

      val string = serializer.toBytes("123")
      string.contentType shouldBe jsonContentTypeWith("string")
      serializer.fromBytes(string) shouldBe "123"
      serializer.fromBytes(new BytesPayload(string.bytes, jsonContentTypeWith("java.lang.String"))) shouldBe "123"

      val boolean = serializer.toBytes(true)
      boolean.contentType shouldBe jsonContentTypeWith("boolean")
      serializer.fromBytes(boolean) shouldBe true
      serializer.fromBytes(new BytesPayload(boolean.bytes, jsonContentTypeWith("java.lang.Boolean"))) shouldBe true

      val double = serializer.toBytes(123.321d)
      double.contentType shouldBe jsonContentTypeWith("double")
      serializer.fromBytes(double) shouldBe 123.321d
      serializer.fromBytes(new BytesPayload(double.bytes, jsonContentTypeWith("java.lang.Double"))) shouldBe 123.321d

      val float = serializer.toBytes(123.321f)
      float.contentType shouldBe jsonContentTypeWith("float")
      serializer.fromBytes(float) shouldBe 123.321f
      serializer.fromBytes(new BytesPayload(float.bytes, jsonContentTypeWith("java.lang.Float"))) shouldBe 123.321f

      val short = serializer.toBytes(java.lang.Short.valueOf("1"))
      short.contentType shouldBe jsonContentTypeWith("short")
      serializer.fromBytes(short) shouldBe java.lang.Short.valueOf("1")
      serializer.fromBytes(
        new BytesPayload(short.bytes, jsonContentTypeWith("java.lang.Short"))) shouldBe java.lang.Short.valueOf("1")

      val char = serializer.toBytes('a')
      char.contentType shouldBe jsonContentTypeWith("char")
      serializer.fromBytes(char) shouldBe 'a'
      serializer.fromBytes(new BytesPayload(char.bytes, jsonContentTypeWith("java.lang.Character"))) shouldBe 'a'

      val byte = serializer.toBytes(1.toByte)
      byte.contentType shouldBe jsonContentTypeWith("byte")
      serializer.fromBytes(byte) shouldBe 1.toByte
      serializer.fromBytes(new BytesPayload(byte.bytes, jsonContentTypeWith("java.lang.Byte"))) shouldBe 1.toByte
    }

    "default to FQCN for contentType" in {
      val encoded = serializer.toBytes(SimpleClass("abc", 10))
      encoded.contentType shouldBe jsonContentTypeWith(
        "akka.javasdk.impl.serialization.JsonSerializationSpec$SimpleClass")
    }

    "add version number to contentType" in {
      //new codec to avoid collision with SimpleClass
      val encoded = new JsonSerializer().toBytes(SimpleClassUpdated("abc", 10, 123))
      encoded.contentType shouldBe jsonContentTypeWith(
        "akka.javasdk.impl.serialization.JsonSerializationSpec$SimpleClassUpdated#1")
    }

    "decode with new schema version" in {
      val encoded = serializer.toBytes(SimpleClass("abc", 10))
      val decoded =
        serializer.fromBytes(classOf[SimpleClassUpdated], encoded)
      decoded shouldBe SimpleClassUpdated("abc", 10, 1)
    }

    "fail with the same type name" in {
      //fill the cache
      serializer.toBytes(Dog("abc"))
      assertThrows[IllegalStateException] {
        // both have the same type name "animal"
        serializer.toBytes(Cat("abc"))
      }
    }

    "encode message" in {
      val value = SimpleClass("abc", 10)
      val encoded = serializer.toBytes(value)
      encoded.bytes.utf8String shouldBe """{"str":"abc","in":10}"""
    }

    "decode message with expected type" in {
      val value = SimpleClass("abc", 10)
      val encoded = serializer.toBytes(value)
      val decoded = serializer.fromBytes(value.getClass, encoded)
      decoded shouldBe value
      // without known type name
      val decoded2 = new serialization.JsonSerializer().fromBytes(value.getClass, encoded)
      decoded2 shouldBe value
    }

    "decode message" in {
      val value = SimpleClass("abc", 10)
      val encoded = serializer.toBytes(value)
      val decoded = serializer.fromBytes(encoded)
      decoded shouldBe value
    }

    "fail decode message without known type" in {
      val value = SimpleClass("abc", 10)
      val encoded = serializer.toBytes(value)
      val exception = intercept[IllegalStateException] {
        new serialization.JsonSerializer().fromBytes(encoded)
      }
      exception.getMessage should include("Class mapping not found")
    }

    "decode message with new version" in {
      //old schema
      val value = SimpleClass("abc", 10)
      val encoded = new JsonSerializer().toBytes(value)

      //new schema, simulating restart
      val messageCodecAfterRestart = new JsonSerializer()
      messageCodecAfterRestart.contentTypeFor(classOf[SimpleClassUpdated])
      val decoded = messageCodecAfterRestart.fromBytes(encoded)

      decoded shouldBe SimpleClassUpdated(value.str, value.in, 1)
    }

    {
      import JsonSerializationSpec.AnnotatedWithTypeName.Elephant
      import JsonSerializationSpec.AnnotatedWithTypeName.IndianElephant
      import JsonSerializationSpec.AnnotatedWithTypeName.Lion

      "fail when using the same TypeName" in {
        val encodedElephant = serializer.toBytes(Elephant("Dumbo", 1))
        encodedElephant.contentType shouldBe jsonContentTypeWith("elephant")

        val exception = intercept[IllegalStateException] {
          serializer.toBytes(IndianElephant("Dumbo", 1))
        }

        exception.getMessage shouldBe "Collision with existing existing mapping class akka.javasdk.impl.serialization.JsonSerializationSpec$AnnotatedWithTypeName$Elephant -> elephant. The same type name can't be used for other class class akka.javasdk.impl.serialization.JsonSerializationSpec$AnnotatedWithTypeName$IndianElephant"
      }

      "use TypeName if available" in {

        val encodedLion = serializer.toBytes(Lion("Simba"))
        encodedLion.contentType shouldBe jsonContentTypeWith("lion")

        val encodedElephant = serializer.toBytes(Elephant("Dumbo", 1))
        encodedElephant.contentType shouldBe jsonContentTypeWith("elephant")
      }

    }

    {
      import JsonSerializationSpec.AnnotatedWithEmptyTypeName.Elephant
      import JsonSerializationSpec.AnnotatedWithEmptyTypeName.Lion

      "default to FQCN if TypeName has empty string" in {

        val encodedLion = serializer.toBytes(Lion("Simba"))
        encodedLion.contentType shouldBe jsonContentTypeWith(
          "akka.javasdk.impl.serialization.JsonSerializationSpec$AnnotatedWithEmptyTypeName$Lion")

        val encodedElephant = serializer.toBytes(Elephant("Dumbo", 1))
        encodedElephant.contentType shouldBe jsonContentTypeWith(
          "akka.javasdk.impl.serialization.JsonSerializationSpec$AnnotatedWithEmptyTypeName$Elephant")
      }

    }

    "throw if receiving null" in {
      val failed = intercept[RuntimeException] {
        serializer.toBytes(null)
      }
      failed.getMessage shouldBe "Don't know how to serialize object of type null."
    }

    "encode a dynamic payload for string" in {
      val payload = serializer.encodeDynamicToAkkaByteString("value", "abc")
      payload.utf8String shouldBe """{"value":"abc"}"""
    }

    "encode a dynamic payload for boolean" in {
      val payloadPrimitive = serializer.encodeDynamicToAkkaByteString("value", true)
      payloadPrimitive.utf8String shouldBe """{"value":true}"""

      val payloadObj = serializer.encodeDynamicToAkkaByteString("value", java.lang.Boolean.valueOf(true))
      payloadObj.utf8String shouldBe """{"value":true}"""
    }

    "encode a dynamic payload for short" in {
      val payloadPrimitive = serializer.encodeDynamicToAkkaByteString("value", 10.toShort)
      payloadPrimitive.utf8String shouldBe """{"value":10}"""

      val payloadObj = serializer.encodeDynamicToAkkaByteString("value", java.lang.Short.valueOf(10.toShort))
      payloadObj.utf8String shouldBe """{"value":10}"""
    }

    "encode a dynamic payload for int" in {
      val payloadPrimitive = serializer.encodeDynamicToAkkaByteString("value", 10)
      payloadPrimitive.utf8String shouldBe """{"value":10}"""

      val payloadObject = serializer.encodeDynamicToAkkaByteString("value", java.lang.Integer.valueOf(10))
      payloadObject.utf8String shouldBe """{"value":10}"""

      val payloadBigOne = serializer.encodeDynamicToAkkaByteString("value", java.math.BigInteger.valueOf(10))
      payloadBigOne.utf8String shouldBe """{"value":10}"""
    }

    "encode a dynamic payload for long" in {
      val payloadPrimitive = serializer.encodeDynamicToAkkaByteString("value", 10L)
      payloadPrimitive.utf8String shouldBe """{"value":10}"""

      val payloadObject = serializer.encodeDynamicToAkkaByteString("value", java.lang.Long.valueOf(10L))
      payloadObject.utf8String shouldBe """{"value":10}"""
    }

    "encode a dynamic payload for float" in {
      val payloadPrimitive = serializer.encodeDynamicToAkkaByteString("value", 10f)
      payloadPrimitive.utf8String shouldBe """{"value":10.0}"""

      val payloadObject = serializer.encodeDynamicToAkkaByteString("value", java.lang.Float.valueOf(10f))
      payloadObject.utf8String shouldBe """{"value":10.0}"""
    }

    "encode a dynamic payload for double" in {
      val payloadPrimitive = serializer.encodeDynamicToAkkaByteString("value", 10d)
      payloadPrimitive.utf8String shouldBe """{"value":10.0}"""

      val payloadObject = serializer.encodeDynamicToAkkaByteString("value", java.lang.Double.valueOf(10d))
      payloadObject.utf8String shouldBe """{"value":10.0}"""

      val payloadBigOne = serializer.encodeDynamicToAkkaByteString("value", java.math.BigDecimal.valueOf(10d))
      payloadBigOne.utf8String shouldBe """{"value":10.0}"""
    }

    "use the provided object mapper" in {
      val customMapper = JsonSerializer.newObjectMapperWithDefaults()
      customMapper.setSerializationInclusion(JsonInclude.Include.NON_ABSENT)
      val customSerializer = new JsonSerializer(customMapper)
      val bytesPayload = customSerializer.toBytes(JsonSerializationSpec.SomeTypeWithOptional(Optional.empty()))
      bytesPayload.bytes.utf8String shouldBe "{}"
    }

  }
}
