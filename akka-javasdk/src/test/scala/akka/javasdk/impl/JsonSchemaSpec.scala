/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import scala.jdk.CollectionConverters._

import akka.javasdk.impl.SomeToolInput.ClassWithRecursiveFields
import akka.javasdk.impl.SomeToolInput.CommonStdlibTypes
import akka.javasdk.impl.SomeToolInput.SomeEnum
import akka.javasdk.impl.SomeToolInput.SomeToolInput1
import akka.javasdk.impl.SomeToolInput.SomeToolInput2
import akka.javasdk.impl.SomeToolInput.SomeToolInput3
import akka.javasdk.impl.SomeToolInput.SomeToolInputWithEnum
import akka.javasdk.impl.SomeToolInput.SomeToolInputWithGenericArray
import akka.javasdk.impl.serialization.JsonSerializer
import akka.runtime.sdk.spi.SpiJsonSchema.JsonSchemaArray
import akka.runtime.sdk.spi.SpiJsonSchema.JsonSchemaBoolean
import akka.runtime.sdk.spi.SpiJsonSchema.JsonSchemaDataType
import akka.runtime.sdk.spi.SpiJsonSchema.JsonSchemaEnum
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

    def enumOf(values: Seq[String], description: String = null): JsonSchemaEnum =
      new JsonSchemaEnum(values, Option(description))
  }
}

class JsonSchemaSpec extends AnyWordSpec with Matchers {
  import JsonSchemaSpec._

  "The JsonSchema" should {

    "extract schema with all primitives" in {
      val result = JsonSchema.jsonSchemaFor(classOf[SomeToolInput1]).asInstanceOf[JsonSchemaObject]

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
      val result = JsonSchema.jsonSchemaFor(classOf[SomeToolInput2]).asInstanceOf[JsonSchemaObject]

      result.properties shouldEqual Map(
        "listOfStrings" -> Schema.array(items = Schema.string(), description = "some strings"),
        "arrayOfStrings" -> Schema.array(items = Schema.string()),
        "setOfStrings" -> Schema.array(items = Schema.string()),
        // semi-supported - no map type in json, but can be represented as object
        "mapOfStrings" -> Schema.jsonObject())
    }

    "extract schema with generic array" in {
      val result =
        JsonSchema.jsonSchemaFor(classOf[SomeToolInputWithGenericArray]).asInstanceOf[JsonSchemaObject]

      result.properties shouldEqual Map(
        "genericArray" -> Schema.array(items = Schema.array(items = Schema.string()), description = "array of lists"),
        "objectArray" -> Schema.array(items = Schema.jsonObject(
          properties = Map("someString" -> Schema.string(description = "a value in the nested object")),
          required = Seq("someString"))))

      result.required.toSet shouldEqual Set("genericArray", "objectArray")
    }

    "extract schema with nested object" in {
      val result = JsonSchema.jsonSchemaFor(classOf[SomeToolInput3]).asInstanceOf[JsonSchemaObject]

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
      val result = JsonSchema.jsonSchemaFor(classOf[ClassWithRecursiveFields]).asInstanceOf[JsonSchemaObject]

      result.properties shouldEqual Map(
        "regular" -> Schema.string(),
        "recursive" -> Schema.jsonObject(),
        "nested" -> Schema.jsonObject(
          properties = Map("recursive" -> Schema.jsonObject()),
          required = Seq("recursive")))
      result.required.toSet shouldEqual Set("regular", "recursive", "nested")
    }

    "extract schema with common Java stdlib types" in {
      val result = JsonSchema.jsonSchemaFor(classOf[CommonStdlibTypes]).asInstanceOf[JsonSchemaObject]

      result.properties shouldEqual Map(
        "instant" -> Schema.string(),
        "map" -> Schema.jsonObject(),
        "localDate" -> Schema.string(),
        "localDateTime" -> Schema.string(),
        "localTime" -> Schema.string(),
        "zonedDateTime" -> Schema.string(),
        "offsetDateTime" -> Schema.string(),
        "duration" -> Schema.string(),
        "uuid" -> Schema.string(),
        "character" -> Schema.string(),
        "boxedCharacter" -> Schema.string())

      result.required.toSet shouldEqual Set(
        "instant",
        "map",
        "localDate",
        "localDateTime",
        "zonedDateTime",
        "offsetDateTime",
        "localTime",
        "duration",
        "uuid",
        "character",
        "boxedCharacter")

    }

    "extract schema of primitive type" in {
      JsonSchema.jsonSchemaFor(classOf[String]) shouldBe a[JsonSchemaString]
      JsonSchema.jsonSchemaFor(classOf[Integer]) shouldBe a[JsonSchemaInteger]
      JsonSchema.jsonSchemaFor(classOf[java.lang.Long]) shouldBe a[JsonSchemaInteger]
      JsonSchema.jsonSchemaFor(classOf[java.lang.Double]) shouldBe a[JsonSchemaNumber]
      JsonSchema.jsonSchemaFor(classOf[java.lang.Float]) shouldBe a[JsonSchemaNumber]
      JsonSchema.jsonSchemaFor(classOf[java.lang.Boolean]) shouldBe a[JsonSchemaBoolean]
    }

    "extract schema for a Java enum" in {
      val result = JsonSchema.jsonSchemaFor(classOf[SomeEnum]).asInstanceOf[JsonSchemaEnum]
      result.values shouldEqual Seq("ONE", "TWO", "THREE")
      result.description shouldBe None
    }

    "extract schema with enum field" in {
      val result = JsonSchema.jsonSchemaFor(classOf[SomeToolInputWithEnum]).asInstanceOf[JsonSchemaObject]
      result.properties shouldEqual Map(
        "required" -> Schema.enumOf(Seq("ONE", "TWO", "THREE"), description = "required choice"),
        "optional" -> Schema.enumOf(Seq("ONE", "TWO", "THREE")))
      result.required shouldEqual Seq("required")
    }

    "extract schema with only the fields Jackson maps" in {
      val mapper = JsonSerializer.internalObjectMapper
      val models = new JsonSchemaJacksonModels
      val examples: Seq[(AnyRef, Set[String])] = Seq(
        new JsonSchemaJacksonModels.WithStaticField("name") -> Set("name"),
        new JsonSchemaJacksonModels.WithJacksonAnnotations("name", "secret", 42) -> Set("full_name", "age"),
        new JsonSchemaJacksonModels.WithIgnoredProperties("name", "internal") -> Set("name"),
        new JsonSchemaJacksonModels.WithNamingStrategy("first", "last") -> Set("first_name", "explicit"),
        new JsonSchemaJacksonModels.WithCustomNaming("first", 42) -> Set("x_firstName", "x_age"),
        new JsonSchemaJacksonModels.ViaInterfaceNaming("first") -> Set("first_name"),
        new JsonSchemaJacksonModels.ViaInterfaceIgnore("name", "internal") -> Set("name"),
        new JsonSchemaJacksonModels.IgnoreSub("a", "b", "c") -> Set("a", "c"),
        new JsonSchemaJacksonModels.WithTransientField("name", "cached") -> Set("name"),
        new JsonSchemaJacksonModels.TransientWithProperty("name", "cached") -> Set("name", "cached"),
        new JsonSchemaJacksonModels.SubClass("base", "sub") -> Set("baseField", "subField"),
        new JsonSchemaJacksonModels.GetterRenamed("name") -> Set("full_name"),
        new JsonSchemaJacksonModels.CreatorRenamed("name") -> Set("full_name"),
        new JsonSchemaJacksonModels.GetterIgnored("name", "secret") -> Set("name"),
        new JsonSchemaJacksonModels.AccessorIgnored("a", "b") -> Set("a"),
        new JsonSchemaJacksonModels.ShadowSub("sub") -> Set("name"),
        new JsonSchemaJacksonModels.ShadowRenamedSub("sub") -> Set("x"),
        new JsonSchemaJacksonModels.DescribedPojo("name") -> Set("name"),
        new models.InnerClass -> Set("name"))

      examples.foreach { case (example, expectedNames) =>
        withClue(example.getClass.getSimpleName) {
          val schema = JsonSchema.jsonSchemaFor(example.getClass).asInstanceOf[JsonSchemaObject]
          schema.properties.keySet shouldEqual expectedNames
          schema.required.toSet shouldEqual expectedNames

          // the same names in both directions of the SDK Jackson configuration
          val json = mapper.writeValueAsString(example)
          mapper.readTree(json).fieldNames().asScala.toSet shouldEqual expectedNames
          // Jackson cannot construct a non-static inner class as a root value
          if (!example.isInstanceOf[models.InnerClass]) {
            val readBack = mapper.readValue(json, example.getClass)
            mapper.readTree(mapper.writeValueAsString(readBack)) shouldEqual mapper.readTree(json)
          }
        }
      }

      val shadowSchema =
        JsonSchema.jsonSchemaFor(classOf[JsonSchemaJacksonModels.ShadowSub]).asInstanceOf[JsonSchemaObject]
      shadowSchema.properties("name") shouldEqual Schema.string()

      val describedSchema =
        JsonSchema.jsonSchemaFor(classOf[JsonSchemaJacksonModels.DescribedPojo]).asInstanceOf[JsonSchemaObject]
      describedSchema.properties("name") shouldEqual Schema.string(description = "the name")
    }

    "bind type variables of generic types" in {
      val item = Schema.jsonObject(
        properties = Map("name" -> Schema.string(), "count" -> Schema.integer()),
        required = Seq("count", "name"))

      JsonSchema.jsonSchemaFor(classOf[JsonSchemaJacksonModels.BoundSub]) shouldEqual Schema.jsonObject(
        properties = Map("value" -> Schema.string()),
        required = Seq("value"))

      val signatures = classOf[JsonSchemaJacksonModels.GenericSignatures]
      JsonSchema.jsonSchemaFor(signatures.getMethod("base").getGenericReturnType) shouldEqual Schema.jsonObject(
        properties = Map("value" -> item),
        required = Seq("value"))

      JsonSchema.jsonSchemaFor(signatures.getMethod("holder").getGenericReturnType) shouldEqual Schema.jsonObject(
        properties = Map(
          "items" -> new JsonSchemaArray(item, None),
          "maybe" -> item,
          "array" -> new JsonSchemaArray(item, None),
          "byName" -> Schema.jsonObject()),
        required = Seq("array", "byName", "items"))

      // a type variable without a binding is an empty object
      JsonSchema.jsonSchemaFor(classOf[JsonSchemaJacksonModels.GenericBase[_]]) shouldEqual Schema.jsonObject(
        properties = Map("value" -> Schema.jsonObject()),
        required = Seq("value"))
    }

    "use the element type of a collection subclass without type parameters" in {
      val result = JsonSchema.jsonSchemaFor(classOf[JsonSchemaJacksonModels.ItemList]).asInstanceOf[JsonSchemaArray]
      result.items.asInstanceOf[JsonSchemaObject].properties.keySet shouldEqual Set("name", "count")
    }

    "use object for types causing exceptions" in {
      // Jackson rejects two getters for the same property
      JsonSchema.jsonSchemaFor(classOf[JsonSchemaJacksonModels.ConflictingGetters]) shouldEqual Schema.jsonObject()
    }

  }

}
