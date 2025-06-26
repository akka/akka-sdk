/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.javasdk.impl.SomeToolInput.ClassWithRecursiveFields
import akka.javasdk.impl.SomeToolInput.CommonStdlibTypes
import akka.javasdk.impl.SomeToolInput.SomeToolInput1
import akka.javasdk.impl.SomeToolInput.SomeToolInput2
import akka.javasdk.impl.SomeToolInput.SomeToolInput3
import akka.runtime.sdk.spi.SpiJsonSchema.JsonSchemaArray
import akka.runtime.sdk.spi.SpiJsonSchema.JsonSchemaBoolean
import akka.runtime.sdk.spi.SpiJsonSchema.JsonSchemaDataType
import akka.runtime.sdk.spi.SpiJsonSchema.JsonSchemaInteger
import akka.runtime.sdk.spi.SpiJsonSchema.JsonSchemaNumber
import akka.runtime.sdk.spi.SpiJsonSchema.JsonSchemaObject
import akka.runtime.sdk.spi.SpiJsonSchema.JsonSchemaString
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

object JsonSchemaSpec {
  object Schema {
    def string(description: String = null): JsonSchemaString =
      new JsonSchemaString(Option(description))

    def integer(description: String = null): JsonSchemaInteger =
      new JsonSchemaInteger(Option(description))

    def number(description: String = null): JsonSchemaNumber =
      new JsonSchemaNumber(Option(description))

    def boolean(description: String = null): JsonSchemaBoolean =
      new JsonSchemaBoolean(Option(description))

    def array(items: JsonSchemaDataType, description: String = null): JsonSchemaArray =
      new JsonSchemaArray(items, Option(description))

    def jsonObject(
        description: String = null,
        properties: Map[String, JsonSchemaDataType] = Map.empty,
        required: Seq[String] = Seq.empty): JsonSchemaObject =
      new JsonSchemaObject(Option(description), properties, required)
  }
}

class JsonSchemaSpec extends AnyWordSpec with Matchers {
  import JsonSchemaSpec._

  "The JsonSchema" should {

    "extract schema with all primitives" in {
      val result = JsonSchema.jsonSchemaFor(classOf[SomeToolInput1])

      val expected = Map(
        "string" -> Schema.string(description = "some description"),
        "optionalString" -> Schema.string(),
        "booleanPrimitive" -> Schema.boolean(),
        "intPrimitive" -> Schema.integer(),
        "longPrimitive" -> Schema.integer(),
        "doublePrimitive" -> Schema.number(),
        "floatPrimitive" -> Schema.number(),
        "bytePrimitive" -> Schema.integer(),
        "boxedBoolean" -> Schema.boolean(),
        "boxedInteger" -> Schema.integer(),
        "boxedLong" -> Schema.integer(),
        "boxedDouble" -> Schema.number(),
        "boxedFloat" -> Schema.number(),
        "boxedByte" -> Schema.integer(),
        "primitiveBooleanArray" -> Schema.array(items = Schema.boolean()),
        "boxedBooleanArray" -> Schema.array(items = Schema.boolean()))

      result.properties.foreach { case (key, value) =>
        expected(key) shouldEqual value
      }

      result.required.toSet shouldEqual Set(
        "string",
        "booleanPrimitive",
        "intPrimitive",
        "longPrimitive",
        "doublePrimitive",
        "floatPrimitive",
        "bytePrimitive",
        "boxedBoolean",
        "boxedInteger",
        "boxedLong",
        "boxedDouble",
        "boxedFloat",
        "boxedByte",
        "primitiveBooleanArray",
        "boxedBooleanArray")
    }

    "extract schema with collection" in {
      val result = JsonSchema.jsonSchemaFor(classOf[SomeToolInput2])

      result.properties shouldEqual Map(
        "listOfStrings" -> Schema.array(items = Schema.string(), description = "some strings"))
    }

    "extract schema with nested object" in {
      val result = JsonSchema.jsonSchemaFor(classOf[SomeToolInput3])

      result.properties shouldEqual Map(
        "nestedObject" -> Schema.jsonObject(
          description = "a nested object",
          properties = Map("someString" -> Schema.string(description = "a value in the nested object")),
          required = Seq("someString")))

      result.required shouldEqual Seq("nestedObject")
    }

    "extract parameter list as schema" in {
      val method = classOf[SomeToolInput].getDeclaredMethods.find(_.getName == "someMethod").get
      val result = JsonSchema.jsonSchemaFor(method)

      result.properties.keys shouldEqual Set("input1", "input2", "input3")
      result.properties("input1").description shouldEqual Some("some tool input")
    }

    "extract schema with recursive types" in {
      val result = JsonSchema.jsonSchemaFor(classOf[ClassWithRecursiveFields])

      result.properties shouldEqual Map(
        "regular" -> Schema.string(),
        "recursive" -> Schema.jsonObject(),
        "nested" -> Schema.jsonObject(
          properties = Map("recursive" -> Schema.jsonObject()),
          required = Seq("recursive")))
      result.required.toSet shouldEqual Set("regular", "recursive", "nested")
    }

    "extract schema with common Java stdlib types" in {
      val result = JsonSchema.jsonSchemaFor(classOf[CommonStdlibTypes])

      result.properties shouldEqual Map(
        "instant" -> Schema.string(),
        "map" -> Schema.jsonObject(),
        "localDate" -> Schema.string(),
        "localDateTime" -> Schema.string(),
        "localTime" -> Schema.string(),
        "zonedDateTime" -> Schema.string(),
        "duration" -> Schema.string())

      result.required.toSet shouldEqual Set(
        "instant",
        "map",
        "localDate",
        "localDateTime",
        "zonedDateTime",
        "localTime",
        "duration")

    }

  }

}
