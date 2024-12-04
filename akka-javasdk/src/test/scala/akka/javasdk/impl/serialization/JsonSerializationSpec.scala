/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.serialization

import java.util

import akka.javasdk.JsonMigration
import akka.javasdk.annotations.Migration
import akka.javasdk.annotations.TypeName
import akka.javasdk.impl.serialization
import akka.javasdk.impl.serialization.JsonSerializationSpec.Cat
import akka.javasdk.impl.serialization.JsonSerializationSpec.Dog
import akka.javasdk.impl.serialization.JsonSerializationSpec.SimpleClass
import akka.javasdk.impl.serialization.JsonSerializationSpec.SimpleClassUpdated
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.IntNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

object JsonSerializationSpec {

  @JsonCreator
  @TypeName("animal")
  final case class Dog(str: String)

  @JsonCreator
  @TypeName("animal")
  final case class Cat(str: String)

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

}
class JsonSerializationSpec extends AnyWordSpec with Matchers {

  def jsonTypeUrlWith(typ: String) = JsonSerializer.JsonContentTypePrefix + typ

  val messageCodec = new JsonSerializer

  "The JsonSerializer" should {

// FIXME
//    "check java primitives backward compatibility" in {
//      val integer = messageCodec.encodeScala(123)
//      integer.typeUrl shouldBe jsonTypeUrlWith("int")
//      new StrictJsonMessageCodec(messageCodec).decodeMessage(
//        integer.copy(typeUrl = jsonTypeUrlWith("java.lang.Integer"))) shouldBe 123
//
//      val long = messageCodec.encodeScala(123L)
//      long.typeUrl shouldBe jsonTypeUrlWith("long")
//      new StrictJsonMessageCodec(messageCodec).decodeMessage(
//        long.copy(typeUrl = jsonTypeUrlWith("java.lang.Long"))) shouldBe 123
//
//      val string = messageCodec.encodeScala("123")
//      string.typeUrl shouldBe jsonTypeUrlWith("string")
//      new StrictJsonMessageCodec(messageCodec).decodeMessage(
//        string.copy(typeUrl = jsonTypeUrlWith("java.lang.String"))) shouldBe "123"
//
//      val boolean = messageCodec.encodeScala(true)
//      boolean.typeUrl shouldBe jsonTypeUrlWith("boolean")
//      new StrictJsonMessageCodec(messageCodec).decodeMessage(
//        boolean.copy(typeUrl = jsonTypeUrlWith("java.lang.Boolean"))) shouldBe true
//
//      val double = messageCodec.encodeScala(123.321d)
//      double.typeUrl shouldBe jsonTypeUrlWith("double")
//      new StrictJsonMessageCodec(messageCodec).decodeMessage(
//        double.copy(typeUrl = jsonTypeUrlWith("java.lang.Double"))) shouldBe 123.321d
//
//      val float = messageCodec.encodeScala(123.321f)
//      float.typeUrl shouldBe jsonTypeUrlWith("float")
//      new StrictJsonMessageCodec(messageCodec).decodeMessage(
//        float.copy(typeUrl = jsonTypeUrlWith("java.lang.Float"))) shouldBe 123.321f
//
//      val short = messageCodec.encodeScala(lang.Short.valueOf("1"))
//      short.typeUrl shouldBe jsonTypeUrlWith("short")
//      new StrictJsonMessageCodec(messageCodec).decodeMessage(
//        short.copy(typeUrl = jsonTypeUrlWith("java.lang.Short"))) shouldBe lang.Short.valueOf("1")
//
//      val char = messageCodec.encodeScala('a')
//      char.typeUrl shouldBe jsonTypeUrlWith("char")
//      new StrictJsonMessageCodec(messageCodec).decodeMessage(
//        char.copy(typeUrl = jsonTypeUrlWith("java.lang.Character"))) shouldBe 'a'
//
//      val byte = messageCodec.encodeScala(1.toByte)
//      byte.typeUrl shouldBe jsonTypeUrlWith("byte")
//      new StrictJsonMessageCodec(messageCodec).decodeMessage(
//        byte.copy(typeUrl = jsonTypeUrlWith("java.lang.Byte"))) shouldBe 1.toByte
//    }

    "default to FQCN for contentType" in {
      val encoded = messageCodec.toBytes(SimpleClass("abc", 10))
      encoded.contentType shouldBe jsonTypeUrlWith("akka.javasdk.impl.serialization.JsonSerializationSpec$SimpleClass")
    }

    "add version number to contentType" in {
      //new codec to avoid collision with SimpleClass
      val encoded = new JsonSerializer().toBytes(SimpleClassUpdated("abc", 10, 123))
      encoded.contentType shouldBe jsonTypeUrlWith(
        "akka.javasdk.impl.serialization.JsonSerializationSpec$SimpleClassUpdated#1")
    }

    "decode with new schema version" in {
      val encoded = messageCodec.toBytes(SimpleClass("abc", 10))
      val decoded =
        messageCodec.fromBytes(classOf[SimpleClassUpdated], encoded)
      decoded shouldBe SimpleClassUpdated("abc", 10, 1)
    }

    "fail with the same type name" in {
      //fill the cache
      messageCodec.toBytes(Dog("abc"))
      assertThrows[IllegalStateException] {
        // both have the same type name "animal"
        messageCodec.toBytes(Cat("abc"))
      }
    }

    "encode message" in {
      val value = SimpleClass("abc", 10)
      val encoded = messageCodec.toBytes(value)
      encoded.bytes.utf8String shouldBe """{"str":"abc","in":10}"""
    }

    "decode message with expected type" in {
      val value = SimpleClass("abc", 10)
      val encoded = messageCodec.toBytes(value)
      val decoded = messageCodec.fromBytes(value.getClass, encoded)
      decoded shouldBe value
      // without known type name
      val decoded2 = new serialization.JsonSerializer().fromBytes(value.getClass, encoded)
      decoded2 shouldBe value
    }

    "decode message" in {
      val value = SimpleClass("abc", 10)
      val encoded = messageCodec.toBytes(value)
      val decoded = messageCodec.fromBytes(encoded)
      decoded shouldBe value
    }

    "fail decode message without known type" in {
      val value = SimpleClass("abc", 10)
      val encoded = messageCodec.toBytes(value)
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
        val encodedElephant = messageCodec.toBytes(Elephant("Dumbo", 1))
        encodedElephant.contentType shouldBe jsonTypeUrlWith("elephant")

        val exception = intercept[IllegalStateException] {
          messageCodec.toBytes(IndianElephant("Dumbo", 1))
        }

        exception.getMessage shouldBe "Collision with existing existing mapping class akka.javasdk.impl.serialization.JsonSerializationSpec$AnnotatedWithTypeName$Elephant -> elephant. The same type name can't be used for other class class akka.javasdk.impl.serialization.JsonSerializationSpec$AnnotatedWithTypeName$IndianElephant"
      }

      "use TypeName if available" in {

        val encodedLion = messageCodec.toBytes(Lion("Simba"))
        encodedLion.contentType shouldBe jsonTypeUrlWith("lion")

        val encodedElephant = messageCodec.toBytes(Elephant("Dumbo", 1))
        encodedElephant.contentType shouldBe jsonTypeUrlWith("elephant")
      }

    }

    {
      import JsonSerializationSpec.AnnotatedWithEmptyTypeName.Elephant
      import JsonSerializationSpec.AnnotatedWithEmptyTypeName.Lion

      "default to FQCN if TypeName has empty string" in {

        val encodedLion = messageCodec.toBytes(Lion("Simba"))
        encodedLion.contentType shouldBe jsonTypeUrlWith(
          "akka.javasdk.impl.serialization.JsonSerializationSpec$AnnotatedWithEmptyTypeName$Lion")

        val encodedElephant = messageCodec.toBytes(Elephant("Dumbo", 1))
        encodedElephant.contentType shouldBe jsonTypeUrlWith(
          "akka.javasdk.impl.serialization.JsonSerializationSpec$AnnotatedWithEmptyTypeName$Elephant")
      }

    }

    "throw if receiving null" in {
      val failed = intercept[RuntimeException] {
        messageCodec.toBytes(null)
      }
      failed.getMessage shouldBe "Don't know how to serialize object of type null."
    }

  }
}
