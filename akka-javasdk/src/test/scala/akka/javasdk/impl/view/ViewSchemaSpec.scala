/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.view

import akka.javasdk.testmodels.view.ViewTestModels
import akka.runtime.sdk.spi.SpiSchema.SpiBoolean
import akka.runtime.sdk.spi.SpiSchema.SpiByteString
import akka.runtime.sdk.spi.SpiSchema.SpiClass
import akka.runtime.sdk.spi.SpiSchema.SpiClassRef
import akka.runtime.sdk.spi.SpiSchema.SpiDouble
import akka.runtime.sdk.spi.SpiSchema.SpiEnum
import akka.runtime.sdk.spi.SpiSchema.SpiField
import akka.runtime.sdk.spi.SpiSchema.SpiFloat
import akka.runtime.sdk.spi.SpiSchema.SpiInteger
import akka.runtime.sdk.spi.SpiSchema.SpiList
import akka.runtime.sdk.spi.SpiSchema.SpiLong
import akka.runtime.sdk.spi.SpiSchema.SpiNumeric
import akka.runtime.sdk.spi.SpiSchema.SpiOptional
import akka.runtime.sdk.spi.SpiSchema.SpiString
import akka.runtime.sdk.spi.SpiSchema.SpiTimestamp
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ViewSchemaSpec extends AnyWordSpec with Matchers {

  "The view schema" should {

    "handle all kinds of types" in {
      val everyTypeSchema = ViewSchema(classOf[ViewTestModels.EveryType])

      everyTypeSchema match {
        case clazz: SpiClass =>
          clazz.name shouldEqual (classOf[ViewTestModels.EveryType].getName)
          val expectedFields = Seq(
            "intValue" -> SpiInteger,
            "longValue" -> SpiLong,
            "floatValue" -> SpiFloat,
            "doubleValue" -> SpiDouble,
            "booleanValue" -> SpiBoolean,
            "stringValue" -> SpiString,
            "wrappedInt" -> SpiInteger,
            "wrappedLong" -> SpiLong,
            "wrappedFloat" -> SpiFloat,
            "wrappedDouble" -> SpiDouble,
            "wrappedBoolean" -> SpiBoolean,
            "instant" -> SpiTimestamp,
            "bytes" -> SpiByteString,
            "optionalString" -> new SpiOptional(SpiString),
            "repeatedString" -> new SpiList(SpiString),
            "nestedMessage" -> new SpiClass(
              classOf[ViewTestModels.ByEmail].getName,
              Seq(new SpiField("email", SpiString))),
            "anEnum" -> new SpiEnum(classOf[ViewTestModels.AnEnum].getName),
            "bigDecimal" -> SpiNumeric)
          clazz.fields should have size expectedFields.size

          expectedFields.foreach { case (name, expectedType) =>
            clazz.getField(name).get.fieldType shouldBe expectedType
          }

        case _ => fail()
      }
    }

    "map google.protobuf.Timestamp to SpiTimestamp" in {
      ViewSchema(classOf[com.google.protobuf.Timestamp]) shouldBe SpiTimestamp
    }

    "map protobuf fields with explicit presence to SpiOptional" in {
      val result = ViewSchema(classOf[presence.PresenceProtos.PresenceFields]).asInstanceOf[SpiClass]
      result.fields should have size 7
      val fieldTypes = result.fields.map(f => f.name -> f.fieldType).toMap
      fieldTypes("id") shouldBe SpiString
      fieldTypes("nested") shouldBe new SpiOptional(
        new SpiClass("presence.Nested", Seq(new SpiField("value", SpiString))))
      fieldTypes("nickname") shouldBe new SpiOptional(SpiString)
      fieldTypes("review_date") shouldBe new SpiOptional(SpiTimestamp)
      fieldTypes("tags") shouldBe new SpiList(SpiString)
      fieldTypes("email") shouldBe new SpiOptional(SpiString)
      fieldTypes("phone") shouldBe new SpiOptional(SpiString)
    }

    "not wrap protobuf fields in SpiOptional for query types" in {
      val result = ViewSchema.forQuery(classOf[presence.PresenceProtos.PresenceFields]).asInstanceOf[SpiClass]
      val fieldTypes = result.fields.map(f => f.name -> f.fieldType).toMap
      fieldTypes("nested") shouldBe a[SpiClass]
      fieldTypes("nickname") shouldBe SpiString
      fieldTypes("review_date") shouldBe SpiTimestamp
      fieldTypes("tags") shouldBe new SpiList(SpiString)
    }

    "handle self referencing type trees" in {
      val result = ViewSchema(classOf[ViewTestModels.Recursive])
      result shouldBe a[SpiClass]
      result.asInstanceOf[SpiClass].getField("child").get.fieldType shouldBe new SpiClassRef(
        classOf[ViewTestModels.Recursive].getName)
    }

    "handle self referencing type trees with longer cycles" in {
      val result = ViewSchema(classOf[ViewTestModels.TwoStepRecursive])
      result shouldBe a[SpiClass]
      result
        .asInstanceOf[SpiClass]
        .getField("child")
        .get
        .fieldType
        .asInstanceOf[SpiClass]
        .getField("recursive")
        .get
        .fieldType shouldBe new SpiClassRef(classOf[ViewTestModels.TwoStepRecursive].getName)
    }

    "handle recursive protobuf messages" in {
      val result = ViewSchema(classOf[protoconsumer.EventsForConsumer.CircularParent])
      result shouldBe a[SpiClass]
      result
        .asInstanceOf[SpiClass]
        .getField("child")
        .get
        .fieldType
        .asInstanceOf[SpiOptional]
        .valueType
        .asInstanceOf[SpiClass]
        .getField("parent")
        .get
        .fieldType shouldBe new SpiOptional(new SpiClassRef("protoconsumer.CircularParent"))
    }

    "handle google.protobuf.Struct" in {
      val result = ViewSchema(classOf[com.google.protobuf.Struct])
      result shouldBe a[SpiClass]
    }
  }

}
