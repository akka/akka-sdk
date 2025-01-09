/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
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
            "anEnum" -> new SpiEnum(classOf[ViewTestModels.AnEnum].getName))
          clazz.fields should have size expectedFields.size

          expectedFields.foreach { case (name, expectedType) =>
            clazz.getField(name).get.fieldType shouldBe expectedType
          }

        case _ => fail()
      }
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
  }

}
