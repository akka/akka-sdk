/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.javasdk.impl.SomeToolInput.SomeToolInput1
import akka.javasdk.impl.SomeToolInput.SomeToolInput2
import akka.javasdk.impl.SomeToolInput.SomeToolInput3
import akka.runtime.sdk.spi.SpiJsonSchema.JsonSchemaArray
import akka.runtime.sdk.spi.SpiJsonSchema.JsonSchemaBoolean
import akka.runtime.sdk.spi.SpiJsonSchema.JsonSchemaInteger
import akka.runtime.sdk.spi.SpiJsonSchema.JsonSchemaNumber
import akka.runtime.sdk.spi.SpiJsonSchema.JsonSchemaObject
import akka.runtime.sdk.spi.SpiJsonSchema.JsonSchemaString
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class JsonSchemaSpec extends AnyWordSpec with Matchers {

  "The JsonSchema" should {

    "extract schema with all primitives" in {
      val result = JsonSchema.jsonSchemaFor(classOf[SomeToolInput1])

      result.properties shouldEqual Map(
        "string" -> new JsonSchemaString(description = Some("some description")),
        "optionalString" -> new JsonSchemaString(description = None),
        "booleanPrimitive" -> new JsonSchemaBoolean(description = None),
        "intPrimitive" -> new JsonSchemaInteger(description = None),
        "longPrimitive" -> new JsonSchemaInteger(description = None),
        "doublePrimitive" -> new JsonSchemaNumber(description = None),
        "floatPrimitive" -> new JsonSchemaNumber(description = None),
        "bytePrimitive" -> new JsonSchemaInteger(description = None),
        "boxedBoolean" -> new JsonSchemaBoolean(description = None),
        "boxedInteger" -> new JsonSchemaInteger(description = None),
        "boxedLong" -> new JsonSchemaInteger(description = None),
        "boxedDouble" -> new JsonSchemaNumber(description = None),
        "boxedFloat" -> new JsonSchemaNumber(description = None),
        "boxedByte" -> new JsonSchemaInteger(description = None))

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
        "boxedByte")
    }

    "extract schema with collection" in {
      val result = JsonSchema.jsonSchemaFor(classOf[SomeToolInput2])

      result.properties shouldEqual Map(
        "listOfStrings" -> new JsonSchemaArray(items = new JsonSchemaString(None), description = Some("some strings")))
    }

    "extract schema with nested object" in {
      val result = JsonSchema.jsonSchemaFor(classOf[SomeToolInput3])

      result.properties shouldEqual Map(
        "nestedObject" -> new JsonSchemaObject(
          description = Some("some nested object"),
          properties = Map("someString" -> new JsonSchemaString(Some("a value in the nested object"))),
          required = Seq("someString")))

      result.required shouldEqual Seq("nestedObject")
    }
  }

}
