/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.view

import akka.javasdk.testmodels.view.ViewTestModels
import akka.runtime.sdk.spi.views.SpiType.SpiBoolean
import akka.runtime.sdk.spi.views.SpiType.SpiByteString
import akka.runtime.sdk.spi.views.SpiType.SpiClass
import akka.runtime.sdk.spi.views.SpiType.SpiDouble
import akka.runtime.sdk.spi.views.SpiType.SpiField
import akka.runtime.sdk.spi.views.SpiType.SpiFloat
import akka.runtime.sdk.spi.views.SpiType.SpiInteger
import akka.runtime.sdk.spi.views.SpiType.SpiList
import akka.runtime.sdk.spi.views.SpiType.SpiLong
import akka.runtime.sdk.spi.views.SpiType.SpiOptional
import akka.runtime.sdk.spi.views.SpiType.SpiString
import akka.runtime.sdk.spi.views.SpiType.SpiTimestamp
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
              "akka.javasdk.testmodels.view.ViewTestModels$ByEmail",
              Seq(new SpiField("email", SpiString))))
          clazz.fields should have size expectedFields.size

          expectedFields.foreach { case (name, expectedType) =>
            clazz.getField(name).get.fieldType shouldBe expectedType
          }

        case _ => fail()
      }
    }

    // FIXME self-referencing/recursive types
  }

}
